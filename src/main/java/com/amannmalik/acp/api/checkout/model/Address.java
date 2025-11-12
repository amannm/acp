package com.amannmalik.acp.api.checkout.model;

import com.amannmalik.acp.util.Ensure;

import java.util.Locale;
import java.util.regex.Pattern;

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
    private static final Pattern ISO_COUNTRY = Pattern.compile("^[A-Z]{2}$");

    public Address {
        name = Ensure.nonBlank("address.name", name);
        ensureMaxLength("address.name", name, NAME_MAX);
        lineOne = Ensure.nonBlank("address.line_one", lineOne);
        ensureMaxLength("address.line_one", lineOne, LINE_MAX);
        if (lineTwo != null) {
            lineTwo = lineTwo.trim();
            if (!lineTwo.isEmpty()) {
                ensureMaxLength("address.line_two", lineTwo, LINE_MAX);
            } else {
                lineTwo = "";
            }
        }
        city = Ensure.nonBlank("address.city", city);
        ensureMaxLength("address.city", city, CITY_MAX);
        state = normalizeState(state);
        country = normalizeCountry(country);
        postalCode = Ensure.nonBlank("address.postal_code", postalCode);
        ensureMaxLength("address.postal_code", postalCode, POSTAL_MAX);
    }

    private static String normalizeState(String value) {
        if (value == null) {
            return null;
        }
        var trimmed = value.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("address.state MUST be non-blank when provided");
        }
        ensureMaxLength("address.state", trimmed, LINE_MAX);
        return trimmed;
    }

    private static String normalizeCountry(String value) {
        var normalized = Ensure.nonBlank("address.country", value).toUpperCase(Locale.ROOT);
        if (!ISO_COUNTRY.matcher(normalized).matches()) {
            throw new IllegalArgumentException("address.country MUST be ISO-3166-1 alpha-2");
        }
        return normalized;
    }

    private static void ensureMaxLength(String field, String value, int max) {
        if (value.length() > max) {
            throw new IllegalArgumentException(field + " MUST be <= " + max + " characters");
        }
    }
}
