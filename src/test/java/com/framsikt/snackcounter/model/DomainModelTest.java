package com.framsikt.snackcounter.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class DomainModelTest {
    @Test
    void officesHaveSeparateLocalCurrencyBudgets() {
        assertEquals(new Amount(new BigDecimal("6000"), SnackCurrency.NOK), Office.OSLO.monthlyBudget());
        assertEquals(new Amount(new BigDecimal("15000"), SnackCurrency.INR), Office.BENGALURU.monthlyBudget());
    }

    @Test
        void amountPreservesExactMinorUnitsAndAllowsZero() {
                Amount amount = new Amount(new BigDecimal("300.5"), SnackCurrency.NOK);

                assertEquals(new BigDecimal("300.50"), amount.value());
                assertEquals(SnackCurrency.NOK, amount.currency());
                assertEquals(new Amount(new BigDecimal("300.50"), SnackCurrency.NOK), amount);
                assertEquals(new BigDecimal("0.00"), new Amount(BigDecimal.ZERO, SnackCurrency.INR).value());
    }

    @Test
    void amountRejectsNegativeAndFractionalMinorUnits() {
        assertThrows(IllegalArgumentException.class,
                () -> new Amount(new BigDecimal("-0.01"), SnackCurrency.NOK));
        assertThrows(ArithmeticException.class,
                () -> new Amount(new BigDecimal("1.001"), SnackCurrency.INR));
        assertThrows(NullPointerException.class, () -> new Amount(null, SnackCurrency.INR));
        assertThrows(NullPointerException.class, () -> new Amount(BigDecimal.ONE, null));
    }

    @Test
    void addingAmountsRequiresMatchingCurrencies() {
        Amount nok = new Amount(new BigDecimal("300"), SnackCurrency.NOK);

        assertEquals(new Amount(new BigDecimal("500"), SnackCurrency.NOK),
                nok.plus(new Amount(new BigDecimal("200"), SnackCurrency.NOK)));
        assertThrows(IllegalArgumentException.class,
                () -> nok.plus(new Amount(new BigDecimal("200"), SnackCurrency.INR)));
    }

    @Test
    void visitingVolunteerKeepsPayrollCurrencySeparateFromCurrentOffice() {
        Volunteer visitor = new Volunteer("employee-1", Office.OSLO, Office.BENGALURU,
                SnackCurrency.NOK, true);

        assertEquals(Office.OSLO, visitor.homeOffice());
        assertEquals(Office.BENGALURU, visitor.currentOffice());
        assertEquals(SnackCurrency.NOK, visitor.payrollCurrency());
        assertEquals(SnackCurrency.INR, visitor.currentOffice().monthlyBudget().currency());
        assertTrue(visitor.available());
        assertEquals(EmploymentType.VOLUNTEER, visitor.employmentType());
        assertEquals(EmploymentType.CONTRACTOR, new Volunteer("contractor", Office.OSLO, Office.OSLO,
                SnackCurrency.NOK, true, EmploymentType.CONTRACTOR).employmentType());
        assertFalse(new Volunteer("employee-2", Office.BENGALURU, Office.BENGALURU,
                SnackCurrency.INR, false).available());
    }

    @Test
    void claimsRequirePositivePurchaseAmountAndReceiptDetails() {
        Amount amount = new Amount(new BigDecimal("500"), SnackCurrency.INR);
        ReimbursementClaim claim = new ReimbursementClaim("employee-1", amount, "Office snacks",
                new Receipt("receipt-1"), ClaimStatus.PENDING);

        assertEquals(SnackCurrency.INR, claim.amount().currency());
        assertEquals("receipt-1", claim.receipt().imageReference());
        assertNotEquals(ClaimStatus.APPROVED, ClaimStatus.REIMBURSED);
        assertThrows(IllegalArgumentException.class, () -> new ReimbursementClaim("employee-1",
                new Amount(BigDecimal.ZERO, SnackCurrency.INR), "Snacks", new Receipt("receipt-1"),
                ClaimStatus.PENDING));
        assertThrows(IllegalArgumentException.class, () -> new Receipt(" "));
        assertThrows(IllegalArgumentException.class,
                () -> new ReimbursementClaim("employee-1", amount, " ", new Receipt("receipt-1"),
                        ClaimStatus.PENDING));
    }

    @Test
    void volunteerRequiresAnEmployeeId() {
        assertThrows(IllegalArgumentException.class,
                () -> new Volunteer(" ", Office.OSLO, Office.OSLO, SnackCurrency.NOK, true));
    }
}
