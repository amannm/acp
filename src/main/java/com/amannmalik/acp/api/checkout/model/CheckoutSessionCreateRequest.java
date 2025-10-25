package com.amannmalik.acp.api.checkout.model;

import com.amannmalik.acp.util.Ensure;

import java.util.List;

public record CheckoutSessionCreateRequest(
        List<Item> items,
        Buyer buyer,
        Address fulfillmentAddress) {
    public CheckoutSessionCreateRequest {
        items = Ensure.immutableList("create.items", items);
        if (items.isEmpty()) {
            throw new IllegalArgumentException("create.items MUST include at least one item");
        }
    }
}
