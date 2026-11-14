package com.framsikt.snackcounter.model;

/** Approval and payment are separate outcomes reported by the simulated HR system. */
public enum ClaimStatus {
    PENDING,
    APPROVED,
    REJECTED,
    REIMBURSED
}