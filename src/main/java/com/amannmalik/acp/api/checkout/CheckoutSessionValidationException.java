package com.amannmalik.acp.api.checkout;

public final class CheckoutSessionValidationException extends CheckoutSessionException {
    private final String code;
    private final String param;

    public CheckoutSessionValidationException(String message, String code, String param) {
        super(message);
        this.code = code;
        this.param = param;
    }

    public String code() {
        return code;
    }

    public String param() {
        return param;
    }
}
