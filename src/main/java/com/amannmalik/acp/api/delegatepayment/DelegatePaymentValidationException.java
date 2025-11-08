package com.amannmalik.acp.api.delegatepayment;

import com.amannmalik.acp.util.Ensure;

/// Specification: specification/2025-09-29/rfcs/rfc.delegate_payment.md §3.6
public final class DelegatePaymentValidationException extends DelegatePaymentException {
    private final String code;
    private final String param;

    public DelegatePaymentValidationException(String message) {
        this(message, "invalid_card", null);
    }

    public DelegatePaymentValidationException(String message, String code, String param) {
        super(message);
        this.code = Ensure.nonBlank("delegate_payment.error.code", code);
        if (param != null && param.isBlank()) {
            throw new IllegalArgumentException("delegate_payment.error.param MUST be non-blank when provided");
        }
        this.param = param;
    }

    public String code() {
        return code;
    }

    public String param() {
        return param;
    }
}
