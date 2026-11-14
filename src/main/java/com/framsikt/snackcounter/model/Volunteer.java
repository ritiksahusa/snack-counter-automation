package com.framsikt.snackcounter.model;

import java.util.Objects;

/** Availability and current office are explicitly supplied, not inferred from a calendar. */
public record Volunteer(String employeeId, Office homeOffice, Office currentOffice,
                        SnackCurrency payrollCurrency, boolean available) {
    public Volunteer {
        DomainValidation.requireText(employeeId, "employeeId");
        Objects.requireNonNull(homeOffice, "homeOffice");
        Objects.requireNonNull(currentOffice, "currentOffice");
        Objects.requireNonNull(payrollCurrency, "payrollCurrency");
    }
}