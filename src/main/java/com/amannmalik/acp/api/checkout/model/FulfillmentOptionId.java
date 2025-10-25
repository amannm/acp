package com.amannmalik.acp.api.checkout.model;

import com.amannmalik.acp.util.Ensure;

public record FulfillmentOptionId(String value) {
    public FulfillmentOptionId {
        value = Ensure.nonBlank("fulfillment_option_id", value);
    }

    @Override
    public String toString() {
        return value;
    }
}
