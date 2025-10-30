package com.amannmalik.acp.api.delegatepayment;

public final class DelegatePaymentIdempotencyConflictException extends DelegatePaymentException {
    public DelegatePaymentIdempotencyConflictException(String message) {
        super(message);
    }
}
