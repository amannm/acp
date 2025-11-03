package com.amannmalik.acp.api.checkout;

public final class CheckoutSessionValidationException extends CheckoutSessionException {
    private final String code;
    private final String param;
    private final int status;

    public CheckoutSessionValidationException(String message, String code, String param) {
        this(message, code, param, 409);
    }

    public CheckoutSessionValidationException(String message, String code, String param, int status) {
        super(message);
        this.code = code;
        this.param = param;
        this.status = status;
    }

    public String code() {
        return code;
    }

    public String param() {
        return param;
    }

    public int status() {
        return status;
    }
}
