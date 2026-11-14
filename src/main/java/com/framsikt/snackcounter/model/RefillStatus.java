package com.framsikt.snackcounter.model;

/** Names for lifecycle stages; transition rules belong to the refill service. */
public enum RefillStatus {
    OPEN,
    OFFERED,
    ACCEPTED,
    PURCHASED,
    CLAIM_SUBMITTED,
    REIMBURSED
}