package com.framsikt.snackcounter.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.framsikt.snackcounter.model.Amount;
import com.framsikt.snackcounter.model.Office;
import com.framsikt.snackcounter.model.SnackCurrency;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class BudgetServiceTest {
    private static final YearMonth APRIL = YearMonth.of(2026, 4);
    private final BudgetService budgets = new BudgetService();

    @Test
    void reservesAgainstTheOfficeMonthlyLimitAndAcceptsExactCap() {
        var reservation = budgets.reserve("oslo-1", Office.OSLO, APRIL,
                amount("6000", SnackCurrency.NOK));

        assertEquals(amount("6000", SnackCurrency.NOK), reservation.expectedAmount());
        var report = budgets.report(APRIL, new BigDecimal("0.075"));
        assertEquals(amount("6000", SnackCurrency.NOK), office(report, Office.OSLO).reserved());
        assertEquals(amount("0", SnackCurrency.NOK), office(report, Office.OSLO).remaining());
        assertThrows(IllegalStateException.class, () -> budgets.reserve("oslo-2", Office.OSLO,
                APRIL, amount("0.01", SnackCurrency.NOK)));
    }

    @Test
    void reservationsAndPurchasesTogetherCannotExceedMonthlyBudget() {
        budgets.reserve("oslo-1", Office.OSLO, APRIL, amount("4000", SnackCurrency.NOK));
        budgets.recordPurchase("oslo-1", APRIL, amount("3500", SnackCurrency.NOK));

        assertEquals(amount("2500", SnackCurrency.NOK),
                budgets.reserve("oslo-2", Office.OSLO, APRIL,
                        amount("2500", SnackCurrency.NOK)).expectedAmount());
        assertThrows(IllegalStateException.class, () -> budgets.reserve("oslo-3", Office.OSLO,
                APRIL, amount("0.01", SnackCurrency.NOK)));
    }

    @Test
    void releasingAReservationMakesItsBudgetAvailableAgain() {
        budgets.reserve("oslo-1", Office.OSLO, APRIL, amount("5000", SnackCurrency.NOK));

        assertTrue(budgets.releaseReservation("oslo-1"));
        assertFalse(budgets.releaseReservation("oslo-1"));
        assertEquals(amount("6000", SnackCurrency.NOK),
                budgets.reserve("oslo-2", Office.OSLO, APRIL,
                        amount("6000", SnackCurrency.NOK)).expectedAmount());
    }

    @Test
    void purchaseReplacesItsReservationAndReleasesAnyUnusedAmount() {
        budgets.reserve("oslo-1", Office.OSLO, APRIL, amount("1000", SnackCurrency.NOK));
        var purchase = budgets.recordPurchase("oslo-1", APRIL, amount("750", SnackCurrency.NOK));
        var oslo = office(budgets.report(APRIL, new BigDecimal("0.075")), Office.OSLO);

        assertEquals(amount("750", SnackCurrency.NOK), purchase.actualAmount());
        assertEquals(amount("750", SnackCurrency.NOK), oslo.spent());
        assertEquals(amount("0", SnackCurrency.NOK), oslo.reserved());
        assertEquals(amount("5250", SnackCurrency.NOK), oslo.remaining());
        assertThrows(IllegalStateException.class,
                () -> budgets.recordPurchase("oslo-1", APRIL, amount("750", SnackCurrency.NOK)));
    }

    @Test
    void actualPurchaseMayExceedEstimateOnlyWhenRemainingBudgetAllowsIt() {
        budgets.reserve("oslo-1", Office.OSLO, APRIL, amount("1000", SnackCurrency.NOK));
        budgets.reserve("oslo-2", Office.OSLO, APRIL, amount("4800", SnackCurrency.NOK));

        assertEquals(amount("1100", SnackCurrency.NOK),
                budgets.recordPurchase("oslo-1", APRIL, amount("1100", SnackCurrency.NOK)).actualAmount());
        assertThrows(IllegalStateException.class,
                () -> budgets.recordPurchase("oslo-2", APRIL, amount("5000", SnackCurrency.NOK)));
        assertEquals(amount("4800", SnackCurrency.NOK),
                office(budgets.report(APRIL, new BigDecimal("0.075")), Office.OSLO).reserved());
    }

    @Test
    void officeBudgetCurrencyCannotBeChangedByAVisitingEmployee() {
        assertThrows(IllegalArgumentException.class, () -> budgets.reserve("blr-1", Office.BENGALURU,
                APRIL, amount("100", SnackCurrency.NOK)));
        assertThrows(IllegalArgumentException.class, () -> budgets.reserve("oslo-1", Office.OSLO,
                APRIL, amount("100", SnackCurrency.INR)));
    }

    @Test
    void purchaseMustUseTheReservedOfficeCurrencyAndFailedPurchaseKeepsReservation() {
        budgets.reserve("blr-1", Office.BENGALURU, APRIL, amount("1000", SnackCurrency.INR));

        assertThrows(IllegalArgumentException.class,
                () -> budgets.recordPurchase("blr-1", APRIL, amount("100", SnackCurrency.NOK)));
        assertEquals(amount("1000", SnackCurrency.INR),
                office(budgets.report(APRIL, new BigDecimal("0.075")), Office.BENGALURU).reserved());
    }

    @Test
    void bengaluruUsesItsOwnExactMonthlyCap() {
        budgets.reserve("blr-1", Office.BENGALURU, APRIL, amount("15000", SnackCurrency.INR));

        assertThrows(IllegalStateException.class, () -> budgets.reserve("blr-2", Office.BENGALURU,
                APRIL, amount("0.01", SnackCurrency.INR)));
    }

    @Test
    void simultaneousReservationsCannotSpendTheSameRemainingBudgetTwice() throws Exception {
        var executor = Executors.newFixedThreadPool(2);
        var start = new CountDownLatch(1);
        try {
            var first = executor.submit(() -> tryReserve("oslo-1", start));
            var second = executor.submit(() -> tryReserve("oslo-2", start));
            start.countDown();

            assertNotEquals(first.get(5, TimeUnit.SECONDS), second.get(5, TimeUnit.SECONDS));
            assertEquals(amount("4000", SnackCurrency.NOK),
                    office(budgets.report(APRIL, new BigDecimal("0.075")), Office.OSLO).reserved());
        } finally {
            executor.shutdownNow();
        }
    }

    private boolean tryReserve(String refillId, CountDownLatch start) throws InterruptedException {
        start.await();
        try {
            budgets.reserve(refillId, Office.OSLO, APRIL, amount("4000", SnackCurrency.NOK));
            return true;
        } catch (IllegalStateException expected) {
            return false;
        }
    }

    @Test
    void purchaseIsChargedToItsActualMonthAndOldMonthReservationIsReleased() {
        YearMonth may = APRIL.plusMonths(1);
        budgets.reserve("oslo-1", Office.OSLO, APRIL, amount("1000", SnackCurrency.NOK));
        budgets.recordPurchase("oslo-1", may, amount("900", SnackCurrency.NOK));

        assertEquals(amount("0", SnackCurrency.NOK),
                office(budgets.report(APRIL, new BigDecimal("0.075")), Office.OSLO).reserved());
        assertEquals(amount("900", SnackCurrency.NOK),
                office(budgets.report(may, new BigDecimal("0.075")), Office.OSLO).spent());
    }

    @Test
    void reportKeepsLocalCurrenciesAndConsolidatesOnlyWithExplicitRate() {
        budgets.reserve("oslo-1", Office.OSLO, APRIL, amount("200", SnackCurrency.NOK));
        budgets.recordPurchase("oslo-1", APRIL, amount("100", SnackCurrency.NOK));
        budgets.reserve("blr-1", Office.BENGALURU, APRIL, amount("500", SnackCurrency.INR));
        budgets.recordPurchase("blr-1", APRIL, amount("1000", SnackCurrency.INR));
        budgets.reserve("blr-2", Office.BENGALURU, APRIL, amount("500", SnackCurrency.INR));

        var report = budgets.report(APRIL, new BigDecimal("0.075"));

        assertEquals(amount("100", SnackCurrency.NOK), office(report, Office.OSLO).spent());
        assertEquals(amount("1000", SnackCurrency.INR), office(report, Office.BENGALURU).spent());
        assertEquals(amount("7125", SnackCurrency.NOK), report.totalBudgetNok());
        assertEquals(amount("175", SnackCurrency.NOK), report.totalSpentNok());
        assertEquals(amount("37.50", SnackCurrency.NOK), report.totalReservedNok());
        assertEquals(amount("6912.50", SnackCurrency.NOK), report.totalRemainingNok());
        assertEquals(new BigDecimal("0.075"), report.inrToNokRate());
        assertThrows(UnsupportedOperationException.class, () -> report.offices().clear());
    }

    @Test
    void convertedReportRoundsOnlyTheConsolidatedInrTotalToNokMinorUnits() {
        budgets.reserve("blr-1", Office.BENGALURU, APRIL, amount("1", SnackCurrency.INR));
        budgets.recordPurchase("blr-1", APRIL, amount("1", SnackCurrency.INR));

        assertEquals(amount("0.08", SnackCurrency.NOK),
                budgets.report(APRIL, new BigDecimal("0.075")).totalSpentNok());
    }

    @Test
    void reportRequiresAnExplicitPositiveExchangeRate() {
        assertThrows(NullPointerException.class, () -> budgets.report(APRIL, null));
        assertThrows(IllegalArgumentException.class, () -> budgets.report(APRIL, BigDecimal.ZERO));
        assertThrows(IllegalArgumentException.class,
                () -> budgets.report(APRIL, new BigDecimal("-0.1")));
    }

    private static BudgetService.OfficeSummary office(BudgetService.MonthlyReport report, Office office) {
        return report.offices().stream().filter(summary -> summary.office() == office).findFirst().orElseThrow();
    }

    private static Amount amount(String value, SnackCurrency currency) {
        return new Amount(new BigDecimal(value), currency);
    }
}
