package com.amannmalik.acp.api.checkout.model;

import com.amannmalik.acp.util.Ensure;

public record CheckoutSessionId(String value) {
    public CheckoutSessionId {
        value = Ensure.nonBlank("checkout_session.id", value);
    }

    @Override
    public String toString() {
        return value;
    }
}
