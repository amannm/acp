package com.amannmalik.acp.api.delegatepayment;

public final class DelegatePaymentTokenException extends RuntimeException {
    private final String code;
    private final String param;

    public DelegatePaymentTokenException(String message, String code, String param) {
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
