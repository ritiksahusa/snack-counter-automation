package com.framsikt.snackcounter.service;

import com.framsikt.snackcounter.model.ClaimStatus;
import com.framsikt.snackcounter.model.ReimbursementClaim;
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
                current.claim().amount(), current.claim().description(), current.claim().receipt(), status);
        Submission updated = new Submission(current.refillId(), updatedClaim);
        submissions.put(refillId, updated);
        return updated;
    }

    private static String requireRefillId(String refillId) {
        if (refillId == null || refillId.isBlank()) {
            throw new IllegalArgumentException("refillId must not be blank");
        }
        return refillId;
    }
}
