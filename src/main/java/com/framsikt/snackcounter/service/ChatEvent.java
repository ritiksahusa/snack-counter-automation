package com.framsikt.snackcounter.service;

import com.framsikt.snackcounter.model.Office;
import com.framsikt.snackcounter.model.ClaimStatus;
import java.time.Instant;

/** A simulated chat notification; it contains identifiers and state, not personal data. */
public record ChatEvent(Type type, String refillId, Office office, String boxId,
                        String previousEmployeeId, String employeeId, ClaimStatus claimStatus,
                        Instant occurredAt) {
    public enum Type {
        REFILL_REPORTED,
        OFFERED,
        ACCEPTED,
        DECLINED,
        HANDOFF,
        UNASSIGNED,
        REFILLED,
        CLAIM_SUBMITTED,
        CLAIM_STATUS_UPDATED
    }
}
