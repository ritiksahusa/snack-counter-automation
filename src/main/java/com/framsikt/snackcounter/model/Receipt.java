package com.framsikt.snackcounter.model;

/** A simulation reference to a receipt image, not the image itself. */
public record Receipt(String imageReference) {
    public Receipt {
        DomainValidation.requireText(imageReference, "imageReference");
    }
}