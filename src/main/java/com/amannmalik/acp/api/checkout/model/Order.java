package com.amannmalik.acp.api.checkout.model;

import com.amannmalik.acp.util.Ensure;

import java.net.URI;

public record Order(String id, CheckoutSessionId checkoutSessionId, URI permalinkUrl) {
    public Order {
        id = Ensure.nonBlank("order.id", id);
        checkoutSessionId = Ensure.notNull("order.checkout_session_id", checkoutSessionId);
        permalinkUrl = Ensure.notNull("order.permalink_url", permalinkUrl);
    }
}
