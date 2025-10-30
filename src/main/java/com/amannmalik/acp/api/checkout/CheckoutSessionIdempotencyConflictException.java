package com.amannmalik.acp.api.checkout;

public final class CheckoutSessionIdempotencyConflictException extends CheckoutSessionConflictException {
    public CheckoutSessionIdempotencyConflictException(String message) {
        super(message);
    }
}
