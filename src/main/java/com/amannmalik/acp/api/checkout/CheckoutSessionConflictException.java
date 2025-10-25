package com.amannmalik.acp.api.checkout;

public final class CheckoutSessionConflictException extends CheckoutSessionException {
    public CheckoutSessionConflictException(String message) {
        super(message);
    }
}
