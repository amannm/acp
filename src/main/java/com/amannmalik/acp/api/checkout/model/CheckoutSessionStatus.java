package com.amannmalik.acp.api.checkout.model;

public enum CheckoutSessionStatus {
    NOT_READY_FOR_PAYMENT("not_ready_for_payment"),
    READY_FOR_PAYMENT("ready_for_payment"),
    IN_PROGRESS("in_progress"),
    COMPLETED("completed"),
    CANCELED("canceled");

    private final String jsonValue;

    CheckoutSessionStatus(final String jsonValue) {
        this.jsonValue = jsonValue;
    }

    public String jsonValue() {
        return jsonValue;
    }

    public static CheckoutSessionStatus fromJsonValue(final String value) {
        for (final CheckoutSessionStatus status : values()) {
            if (status.jsonValue.equals(value)) {
                return status;
            }
        }
        throw new IllegalArgumentException("Unknown checkout session status: " + value);
    }
}
