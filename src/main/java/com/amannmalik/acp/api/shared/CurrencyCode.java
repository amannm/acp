package com.amannmalik.acp.api.shared;

import com.amannmalik.acp.util.Ensure;

import java.util.Locale;
import java.util.regex.Pattern;

public record CurrencyCode(String value) {
    private static final Pattern ISO_4217 = Pattern.compile("^[a-z]{3}$");

    public CurrencyCode {
        var normalized = Ensure.nonBlank("currency", value).toLowerCase(Locale.ROOT);
        if (!ISO_4217.matcher(normalized).matches()) {
            throw new IllegalArgumentException("currency MUST be a lowercase ISO-4217 code");
        }
        value = normalized;
    }

    @Override
    public String toString() {
        return value;
    }
}
