package com.amannmalik.acp.api.checkout.model;

import java.util.Objects;

public record CheckoutSessionId(String value) {
    public CheckoutSessionId {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Checkout session id must be non-blank");
        }
    }

    @Override
    public String toString() {
        return value;
    }

    public static CheckoutSessionId from(final String raw) {
        return new CheckoutSessionId(Objects.requireNonNull(raw, "raw"));
    }
}
