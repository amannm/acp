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
    private static final int NAME_MAX = 256;
    private static final int LINE_MAX = 60;
    private static final int CITY_MAX = 60;
    private static final int POSTAL_MAX = 20;

    public Address {
        name = Ensure.nonBlank("address.name", name);
        ensureMaxLength("address.name", name, NAME_MAX);
        lineOne = Ensure.nonBlank("address.line_one", lineOne);
        ensureMaxLength("address.line_one", lineOne, LINE_MAX);
        city = Ensure.nonBlank("address.city", city);
        ensureMaxLength("address.city", city, CITY_MAX);
        state = Ensure.nonBlank("address.state", state);
        ensureMaxLength("address.state", state, LINE_MAX);
        country = Ensure.nonBlank("address.country", country);
        ensureExactLength("address.country", country, 2);
        postalCode = Ensure.nonBlank("address.postal_code", postalCode);
        ensureMaxLength("address.postal_code", postalCode, POSTAL_MAX);
        if (lineTwo != null) {
            ensureMaxLength("address.line_two", lineTwo, LINE_MAX);
        }
    }

    private static void ensureMaxLength(String field, String value, int max) {
        if (value.length() > max) {
            throw new IllegalArgumentException(field + " MUST be <= " + max + " characters");
        }
    }

    private static void ensureExactLength(String field, String value, int expected) {
        if (value.length() != expected) {
            throw new IllegalArgumentException(field + " MUST be exactly " + expected + " characters");
        }
    }
}
