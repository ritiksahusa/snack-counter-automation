package com.framsikt.snackcounter.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

/** Checks related domain data only; the refill and HR workflow is not implemented yet. */
class OfficePurchaseDataScenarioTest {
    @Test
    void visitingOsloVolunteerCanRepresentBengaluruPurchaseAndPendingClaim() {
        Volunteer visitor = new Volunteer("employee-oslo", Office.OSLO, Office.BENGALURU,
                SnackCurrency.NOK, true);
        Refill refill = new Refill("refill-bengaluru", Office.BENGALURU, "box-1", RefillStatus.OPEN);
        Amount purchase = new Amount(new BigDecimal("1250.50"), SnackCurrency.INR);
        Receipt receipt = new Receipt("receipt-bengaluru");
        ReimbursementClaim claim = new ReimbursementClaim(visitor.employeeId(), purchase,
                "Bengaluru snacks", receipt, ClaimStatus.PENDING);

        assertTrue(visitor.available());
        assertEquals(Office.BENGALURU, refill.office());
        assertEquals(refill.office().monthlyBudget().currency(), claim.amount().currency());
        assertEquals(new BigDecimal("15000.00"), refill.office().monthlyBudget().value());
        assertEquals(SnackCurrency.NOK, visitor.payrollCurrency());
        assertEquals(new BigDecimal("1250.50"), claim.amount().value());
        assertEquals(visitor.employeeId(), claim.employeeId());
        assertEquals("Bengaluru snacks", claim.description());
        assertEquals("receipt-bengaluru", claim.receipt().imageReference());
        assertEquals(ClaimStatus.PENDING, claim.status());
    }

    @Test
    void visitingBengaluruVolunteerCanRepresentOsloPurchaseAndPendingClaim() {
        Volunteer visitor = new Volunteer("employee-bengaluru", Office.BENGALURU, Office.OSLO,
                SnackCurrency.INR, true);
        Refill refill = new Refill("refill-oslo", Office.OSLO, "box-1", RefillStatus.OPEN);
        Amount purchase = new Amount(new BigDecimal("850"), SnackCurrency.NOK);
        ReimbursementClaim claim = new ReimbursementClaim(visitor.employeeId(), purchase,
                "Oslo snacks", new Receipt("receipt-oslo"), ClaimStatus.PENDING);

        assertTrue(visitor.available());
        assertEquals(Office.OSLO, visitor.currentOffice());
        assertEquals(Office.OSLO, refill.office());
        assertEquals(new BigDecimal("6000.00"), refill.office().monthlyBudget().value());
        assertEquals(refill.office().monthlyBudget().currency(), claim.amount().currency());
        assertEquals(SnackCurrency.INR, visitor.payrollCurrency());
        assertEquals(new BigDecimal("850.00"), claim.amount().value());
        assertEquals(visitor.employeeId(), claim.employeeId());
        assertEquals("Oslo snacks", claim.description());
        assertEquals("receipt-oslo", claim.receipt().imageReference());
        assertEquals(ClaimStatus.PENDING, claim.status());
    }
}