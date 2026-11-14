package com.framsikt.snackcounter.model;

import java.math.BigDecimal;

/** Each office has a monthly budget in its local purchase currency. */
public enum Office {
    OSLO(new Amount(new BigDecimal("6000"), SnackCurrency.NOK)),
    BENGALURU(new Amount(new BigDecimal("15000"), SnackCurrency.INR));

    private final Amount monthlyBudget;

    Office(Amount monthlyBudget) {
        this.monthlyBudget = monthlyBudget;
    }

    public Amount monthlyBudget() {
        return monthlyBudget;
    }
}