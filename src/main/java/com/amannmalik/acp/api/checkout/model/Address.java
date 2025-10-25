package com.amannmalik.acp.api.checkout.model;

import com.amannmalik.acp.util.Ensure;

public record Address(
        String name,
        String lineOne,
        String lineTwo,
        String city,
        String state,
        String country,
        String postalCode) {
    public Address {
        name = Ensure.nonBlank("address.name", name);
        lineOne = Ensure.nonBlank("address.line_one", lineOne);
        city = Ensure.nonBlank("address.city", city);
        state = Ensure.nonBlank("address.state", state);
        country = Ensure.nonBlank("address.country", country);
        postalCode = Ensure.nonBlank("address.postal_code", postalCode);
        if (lineTwo != null && lineTwo.isBlank()) {
            throw new IllegalArgumentException("address.line_two MUST be non-blank when provided");
        }
    }
}
