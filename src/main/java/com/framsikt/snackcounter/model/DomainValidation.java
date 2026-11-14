package com.framsikt.snackcounter.model;

final class DomainValidation {
    private DomainValidation() {
    }

    static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }
}