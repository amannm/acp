package com.amannmalik.acp.api.checkout.model;

import com.amannmalik.acp.util.Ensure;

public record Buyer(String firstName, String lastName, String email, String phoneNumber) {
    public Buyer {
        firstName = Ensure.nonBlank("buyer.first_name", firstName);
        lastName = Ensure.nonBlank("buyer.last_name", lastName);
        email = Ensure.nonBlank("buyer.email", email);
        if (phoneNumber != null && phoneNumber.isBlank()) {
            throw new IllegalArgumentException("buyer.phone_number MUST be non-blank when provided");
        }
    }
}
