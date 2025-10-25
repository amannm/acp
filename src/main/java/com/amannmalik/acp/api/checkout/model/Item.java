package com.amannmalik.acp.api.checkout.model;

import com.amannmalik.acp.util.Ensure;

public record Item(String id, int quantity) {
    public Item {
        id = Ensure.nonBlank("item.id", id);
        quantity = Ensure.positiveInt("item.quantity", quantity);
    }
}
