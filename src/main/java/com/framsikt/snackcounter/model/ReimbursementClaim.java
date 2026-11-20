package com.framsikt.snackcounter.model;

import java.util.Objects;

/** Claim information, including the original purchase currency. */
public record ReimbursementClaim(String employeeId, EmploymentType employmentType, Amount amount,
                                 String description, Receipt receipt, ClaimStatus status) {
    public ReimbursementClaim(String employeeId, Amount amount, String description,
                              Receipt receipt, ClaimStatus status) {
        this(employeeId, EmploymentType.VOLUNTEER, amount, description, receipt, status);
    }

    public ReimbursementClaim {
        DomainValidation.requireText(employeeId, "employeeId");
        Objects.requireNonNull(employmentType, "employmentType");
        Objects.requireNonNull(amount, "amount");
        if (amount.value().signum() == 0) {
            throw new IllegalArgumentException("Claim amount must be positive");
        }
        DomainValidation.requireText(description, "description");
        Objects.requireNonNull(receipt, "receipt");
        Objects.requireNonNull(status, "status");
    }
}
