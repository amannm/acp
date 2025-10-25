package com.amannmalik.acp.api.checkout.model;

import java.util.List;

public record CheckoutSessionUpdateRequest(
        List<Item> items,
        Buyer buyer,
        Address fulfillmentAddress,
        FulfillmentOptionId fulfillmentOptionId) {
    public CheckoutSessionUpdateRequest {
        if (items != null) {
            items = List.copyOf(items);
            if (items.isEmpty()) {
                throw new IllegalArgumentException("update.items MUST NOT be empty when present");
            }
        }
    }
}
