package com.framsikt.snackcounter.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/** A non-negative monetary amount with a currency and two decimal places. */
public record Amount(BigDecimal value, SnackCurrency currency) {
    public Amount {
        Objects.requireNonNull(value, "value");
        Objects.requireNonNull(currency, "currency");
        if (value.signum() < 0) {
            throw new IllegalArgumentException("Amount must not be negative");
        }
        // NOK and INR have two minor-unit digits; never silently round amounts.
        value = value.setScale(2, RoundingMode.UNNECESSARY);
    }

    public Amount plus(Amount other) {
        Objects.requireNonNull(other, "other");
        if (currency != other.currency) {
            throw new IllegalArgumentException("Cannot add different currencies");
        }
        return new Amount(value.add(other.value), currency);
    }
}