package com.framsikt.snackcounter.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.framsikt.snackcounter.model.Amount;
import com.framsikt.snackcounter.model.ClaimStatus;
import com.framsikt.snackcounter.model.EmploymentType;
import com.framsikt.snackcounter.model.Office;
import com.framsikt.snackcounter.model.Receipt;
import com.framsikt.snackcounter.model.RefillStatus;
import com.framsikt.snackcounter.model.ReimbursementClaim;
import com.framsikt.snackcounter.model.SnackCurrency;
import com.framsikt.snackcounter.model.Volunteer;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class RefillWorkflowTest {
    private static final YearMonth PERIOD = YearMonth.of(2026, 11);
    private final MutableClock clock = new MutableClock();
    private final InMemoryChatGateway chat = new InMemoryChatGateway();
    private final InMemoryHrGateway hr = new InMemoryHrGateway();
    private final BudgetService budgets = new BudgetService();
    private final RefillCoordinator coordinator = new RefillCoordinator(clock, Duration.ofHours(2),
            Duration.ofHours(24), chat);
    private final RefillWorkflow workflow = new RefillWorkflow(coordinator, budgets, hr);

    @Test
    void refillPurchaseAndHrReimbursementRunEndToEndInLocalOfficeCurrency() {
        workflow.upsertVolunteer(new Volunteer("traveler", Office.OSLO, Office.BENGALURU,
                SnackCurrency.NOK, true, EmploymentType.EMPLOYEE));

        var offer = workflow.reportEmpty(Office.BENGALURU, "tea-box");
        var accepted = workflow.accept(offer.id(), "traveler", offer.offerId(), PERIOD,
                amount("900.00", SnackCurrency.INR));
        assertEquals(RefillStatus.ACCEPTED, accepted.status());

        var purchased = workflow.completePurchase(offer.id(), "traveler", PERIOD,
                amount("875.50", SnackCurrency.INR), "Tea and coffee", new Receipt("receipt:demo-42"));
        assertEquals(RefillStatus.PURCHASED, purchased.status());
        var nextRefill = workflow.reportEmpty(Office.BENGALURU, "tea-box");
        assertNotEquals(offer.id(), nextRefill.id(), "A completed purchase allows the next refill cycle");
        assertEquals(new Amount(new BigDecimal("875.50"), SnackCurrency.INR),
                budgets.report(PERIOD, BigDecimal.ONE).offices().stream()
                        .filter(row -> row.office() == Office.BENGALURU).findFirst().orElseThrow().spent());

        HrGateway.Submission submitted = workflow.submitClaim(offer.id());
        ReimbursementClaim payload = submitted.claim();
        assertEquals("traveler", payload.employeeId());
        assertEquals(EmploymentType.EMPLOYEE, payload.employmentType());
        assertEquals(amount("875.50", SnackCurrency.INR), payload.amount());
        assertEquals("Tea and coffee", payload.description());
        assertEquals(new Receipt("receipt:demo-42"), payload.receipt());
        assertEquals(ClaimStatus.PENDING, payload.status());
        assertEquals(RefillStatus.CLAIM_SUBMITTED, coordinator.get(offer.id()).status());

        workflow.updateClaimStatus(offer.id(), ClaimStatus.APPROVED);
        assertEquals(RefillStatus.CLAIM_SUBMITTED, coordinator.get(offer.id()).status(),
                "Approval is not the same as payment");
        workflow.updateClaimStatus(offer.id(), ClaimStatus.REIMBURSED);
        assertEquals(RefillStatus.REIMBURSED, coordinator.get(offer.id()).status());
        assertEquals(ClaimStatus.REIMBURSED, workflow.getClaim(offer.id()).claim().status());
        assertEquals(amount("875.50", SnackCurrency.INR), workflow.claimReport().rows().get(0).totalClaimed());
        assertThrows(IllegalStateException.class, () -> workflow.submitClaim(offer.id()));

        assertEquals(2, chat.events().stream()
                .filter(event -> event.type() == ChatEvent.Type.REFILL_REPORTED).count());
        assertTrue(chat.events().stream().anyMatch(event -> event.type() == ChatEvent.Type.OFFERED));
        assertTrue(chat.events().stream().anyMatch(event -> event.type() == ChatEvent.Type.ACCEPTED));
        assertTrue(chat.events().stream().anyMatch(event -> event.type() == ChatEvent.Type.REFILLED));
        assertTrue(chat.events().stream().anyMatch(event -> event.type() == ChatEvent.Type.CLAIM_SUBMITTED
                && event.claimStatus() == ClaimStatus.PENDING));
        assertTrue(chat.events().stream().anyMatch(event -> event.type() == ChatEvent.Type.CLAIM_STATUS_UPDATED
                && event.claimStatus() == ClaimStatus.APPROVED));
        assertTrue(chat.events().stream().anyMatch(event -> event.type() == ChatEvent.Type.CLAIM_STATUS_UPDATED
                && event.claimStatus() == ClaimStatus.REIMBURSED));
    }

    @Test
    void osloPurchaseRemainsNokForVisitorWhosePayrollCurrencyIsInr() {
                Volunteer visitor = new Volunteer("visitor", Office.BENGALURU, Office.OSLO, SnackCurrency.INR, true);
                workflow.upsertVolunteer(visitor);
        var offer = workflow.reportEmpty(Office.OSLO, "coffee-box");
        workflow.accept(offer.id(), "visitor", offer.offerId(), PERIOD,
                amount("500.00", SnackCurrency.NOK));

        workflow.completePurchase(offer.id(), "visitor", PERIOD, amount("475.00", SnackCurrency.NOK),
                "Coffee", new Receipt("receipt:oslo-demo"));
        HrGateway.Submission submitted = workflow.submitClaim(offer.id());

        assertEquals(SnackCurrency.NOK, submitted.claim().amount().currency());
        assertEquals(SnackCurrency.INR, visitor.payrollCurrency());
        assertEquals(amount("475.00", SnackCurrency.NOK), budgets.report(PERIOD, BigDecimal.ONE)
                .offices().stream().filter(row -> row.office() == Office.OSLO)
                .findFirst().orElseThrow().spent());
    }

    @Test
    void uncompletedAcceptedRefillHandsOffAndReleasesItsBudgetReservation() {
        workflow.upsertVolunteer(new Volunteer("alice", Office.OSLO, Office.OSLO, SnackCurrency.NOK, true));
        workflow.upsertVolunteer(new Volunteer("bob", Office.OSLO, Office.OSLO, SnackCurrency.NOK, true));
        var offer = workflow.reportEmpty(Office.OSLO, "snack-box");
        workflow.accept(offer.id(), "alice", offer.offerId(), PERIOD, amount("1,000.00", SnackCurrency.NOK));

        clock.advance(Duration.ofHours(24));
        var handoff = workflow.expireAssignments().get(0);
        assertEquals("bob", handoff.employeeId());
        assertEquals(RefillStatus.OFFERED, handoff.status());
        var report = budgets.report(PERIOD, BigDecimal.ONE);
        assertEquals(amount("0.00", SnackCurrency.NOK), report.offices().stream()
                .filter(row -> row.office() == Office.OSLO).findFirst().orElseThrow().reserved());
        assertTrue(chat.events().stream().anyMatch(event -> event.type() == ChatEvent.Type.HANDOFF
                && "alice".equals(event.previousEmployeeId()) && "bob".equals(event.employeeId())));
    }

    @Test
    void budgetFailureDoesNotMarkRefillPurchasedOrSubmitClaim() {
        workflow.upsertVolunteer(new Volunteer("alice", Office.OSLO, Office.OSLO, SnackCurrency.NOK, true));
        var offer = workflow.reportEmpty(Office.OSLO, "snack-box");
        workflow.accept(offer.id(), "alice", offer.offerId(), PERIOD, amount("5,900.00", SnackCurrency.NOK));

        assertThrows(IllegalStateException.class, () -> workflow.completePurchase(offer.id(), "alice", PERIOD,
                amount("6,001.00", SnackCurrency.NOK), "Too much", new Receipt("receipt:demo")));
        assertEquals(RefillStatus.ACCEPTED, coordinator.get(offer.id()).status());
        assertThrows(IllegalStateException.class, () -> workflow.submitClaim(offer.id()));
        assertEquals(amount("5,900.00", SnackCurrency.NOK), budgets.report(PERIOD, BigDecimal.ONE)
                .offices().stream().filter(row -> row.office() == Office.OSLO)
                .findFirst().orElseThrow().reserved());
    }

    @Test
    void hrRejectsInvalidClaimTransitionsAndDoesNotTreatApprovalAsReimbursement() {
        ReimbursementClaim claim = new ReimbursementClaim("employee", amount("100.00", SnackCurrency.NOK),
                "Snacks", new Receipt("receipt:demo"), ClaimStatus.PENDING);
        hr.submit("refill-1", claim);
        assertThrows(IllegalStateException.class, () -> hr.updateStatus("refill-1", ClaimStatus.REIMBURSED));
        hr.updateStatus("refill-1", ClaimStatus.APPROVED);
        assertThrows(IllegalStateException.class, () -> hr.updateStatus("refill-1", ClaimStatus.PENDING));
        assertEquals(ClaimStatus.APPROVED, hr.getSubmission("refill-1").claim().status());

        hr.submit("refill-2", claim);
        assertEquals(ClaimStatus.REJECTED, hr.updateStatus("refill-2", ClaimStatus.REJECTED).claim().status());
        assertThrows(IllegalStateException.class, () -> hr.updateStatus("refill-2", ClaimStatus.REIMBURSED));
    }

    private static Amount amount(String value, SnackCurrency currency) {
        return new Amount(new BigDecimal(value.replace(",", "")), currency);
    }

    private static final class MutableClock extends Clock {
        private Instant now = Instant.parse("2026-11-01T10:00:00Z");

        void advance(Duration duration) { now = now.plus(duration); }
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return Clock.fixed(now, zone); }
        @Override public Instant instant() { return now; }
    }
}
