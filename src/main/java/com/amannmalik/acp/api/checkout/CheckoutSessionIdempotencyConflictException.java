package com.amannmalik.acp.api.checkout;

public final class CheckoutSessionIdempotencyConflictException extends CheckoutSessionException {
    public CheckoutSessionIdempotencyConflictException(String message) {
        super(message);
    }
}
