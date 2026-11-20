package com.framsikt.snackcounter.model;

import java.util.Objects;

/** Availability and current office are explicitly supplied, not inferred from a calendar. */
public record Volunteer(String employeeId, Office homeOffice, Office currentOffice,
                        SnackCurrency payrollCurrency, boolean available,
                        EmploymentType employmentType) {
    public Volunteer(String employeeId, Office homeOffice, Office currentOffice,
                     SnackCurrency payrollCurrency, boolean available) {
        this(employeeId, homeOffice, currentOffice, payrollCurrency, available, EmploymentType.VOLUNTEER);
    }

    public Volunteer {
        DomainValidation.requireText(employeeId, "employeeId");
        Objects.requireNonNull(homeOffice, "homeOffice");
        Objects.requireNonNull(currentOffice, "currentOffice");
        Objects.requireNonNull(payrollCurrency, "payrollCurrency");
        Objects.requireNonNull(employmentType, "employmentType");
    }
}
