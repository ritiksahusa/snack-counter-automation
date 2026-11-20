package com.framsikt.snackcounter.service;

import com.framsikt.snackcounter.model.ClaimStatus;
import com.framsikt.snackcounter.model.Amount;
import com.framsikt.snackcounter.model.EmploymentType;
import com.framsikt.snackcounter.model.ReimbursementClaim;
import com.framsikt.snackcounter.model.SnackCurrency;
import java.math.BigDecimal;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/** Simulates HR claim intake and decisions without transmitting employee or receipt data. */
public final class InMemoryHrGateway implements HrGateway {
    private final Map<String, Submission> submissions = new HashMap<>();

    @Override
    public synchronized Submission submit(String refillId, ReimbursementClaim claim) {
        requireRefillId(refillId);
        Objects.requireNonNull(claim, "claim");
        if (claim.status() != ClaimStatus.PENDING) {
            throw new IllegalArgumentException("A submitted claim must start as pending");
        }
        if (submissions.containsKey(refillId)) {
            throw new IllegalStateException("A claim is already submitted for this refill");
        }
        Submission submission = new Submission(refillId, claim);
        submissions.put(refillId, submission);
        return submission;
    }

    @Override
    public synchronized Submission getSubmission(String refillId) {
        Submission submission = submissions.get(requireRefillId(refillId));
        if (submission == null) {
            throw new IllegalArgumentException("No claim is submitted for this refill");
        }
        return submission;
    }

    @Override
    public synchronized Submission updateStatus(String refillId, ClaimStatus status) {
        Objects.requireNonNull(status, "status");
        Submission current = getSubmission(refillId);
        ClaimStatus previous = current.claim().status();
        boolean valid = (previous == ClaimStatus.PENDING
                && (status == ClaimStatus.APPROVED || status == ClaimStatus.REJECTED))
                || (previous == ClaimStatus.APPROVED && status == ClaimStatus.REIMBURSED);
        if (!valid) {
            throw new IllegalStateException("Invalid HR claim status transition: " + previous + " to " + status);
        }
        ReimbursementClaim updatedClaim = new ReimbursementClaim(current.claim().employeeId(),
                current.claim().employmentType(), current.claim().amount(), current.claim().description(),
                current.claim().receipt(), status);
        Submission updated = new Submission(current.refillId(), updatedClaim);
        submissions.put(refillId, updated);
        return updated;
    }

    @Override
    public synchronized ClaimReport claimReport() {
        Map<EmploymentType, Map<SnackCurrency, Total>> totals = new EnumMap<>(EmploymentType.class);
        submissions.values().stream().map(Submission::claim).forEach(claim -> {
            Map<SnackCurrency, Total> byCurrency = totals.computeIfAbsent(claim.employmentType(),
                    ignored -> new EnumMap<>(SnackCurrency.class));
            byCurrency.computeIfAbsent(claim.amount().currency(), ignored -> new Total())
                    .add(claim.amount().value());
        });
        return new ClaimReport(totals.entrySet().stream()
                .flatMap(type -> type.getValue().entrySet().stream()
                        .map(currency -> new ClaimReport.Row(type.getKey(), currency.getKey(),
                                currency.getValue().count,
                                new Amount(currency.getValue().amount, currency.getKey()))))
                .sorted(Comparator.comparing(ClaimReport.Row::employmentType)
                        .thenComparing(ClaimReport.Row::currency))
                .toList());
    }

    private static final class Total {
        private long count;
        private BigDecimal amount = BigDecimal.ZERO;

        private void add(BigDecimal value) {
            count++;
            amount = amount.add(value);
        }
    }

    private static String requireRefillId(String refillId) {
        if (refillId == null || refillId.isBlank()) {
            throw new IllegalArgumentException("refillId must not be blank");
        }
        return refillId;
    }
}
