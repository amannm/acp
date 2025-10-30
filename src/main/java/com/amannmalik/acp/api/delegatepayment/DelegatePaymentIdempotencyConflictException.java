package com.amannmalik.acp.api.delegatepayment;

public final class DelegatePaymentIdempotencyConflictException extends DelegatePaymentConflictException {
    public DelegatePaymentIdempotencyConflictException(String message) {
        super(message);
    }
}
