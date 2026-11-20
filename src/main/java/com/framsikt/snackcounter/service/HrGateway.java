package com.framsikt.snackcounter.service;

import com.framsikt.snackcounter.model.ClaimStatus;
import com.framsikt.snackcounter.model.ReimbursementClaim;

/** Boundary for submitting a claim and reading the simulated HR outcome. */
public interface HrGateway {
    record Submission(String refillId, ReimbursementClaim claim) { }

    Submission submit(String refillId, ReimbursementClaim claim);

    Submission getSubmission(String refillId);

    Submission updateStatus(String refillId, ClaimStatus status);

    ClaimReport claimReport();
}
