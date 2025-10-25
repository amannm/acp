package com.amannmalik.acp.api.checkout;

import com.amannmalik.acp.api.checkout.model.CheckoutSessionId;

public final class CheckoutSessionNotFoundException extends CheckoutSessionException {
    public CheckoutSessionNotFoundException(CheckoutSessionId id) {
        super("Checkout session not found: " + id.value());
    }
}
