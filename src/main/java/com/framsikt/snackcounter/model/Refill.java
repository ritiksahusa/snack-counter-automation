package com.framsikt.snackcounter.model;

import java.util.Objects;

/** Identifies a refill request; assignment and state transitions are a later step. */
public record Refill(String id, Office office, String boxId, RefillStatus status) {
    public Refill {
        DomainValidation.requireText(id, "id");
        Objects.requireNonNull(office, "office");
        DomainValidation.requireText(boxId, "boxId");
        Objects.requireNonNull(status, "status");
    }
}