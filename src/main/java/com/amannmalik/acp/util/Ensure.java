package com.amannmalik.acp.util;

import java.util.List;
import java.util.Objects;

/**
 * Small collection of reusable invariant guards. Keeps constructor code terse while
 * still making violations fail-fast and obvious.
 */
public final class Ensure {
    private Ensure() {
    }

    public static String nonBlank(String field, String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " MUST be non-blank");
        }
        return value;
    }

    public static <T> T notNull(String field, T value) {
        return Objects.requireNonNull(value, field + " MUST NOT be null");
    }

    public static <T> List<T> immutableList(String field, List<T> values) {
        return List.copyOf(notNull(field, values));
    }

    public static long nonNegative(String field, long value) {
        if (value < 0) {
            throw new IllegalArgumentException(field + " MUST be >= 0");
        }
        return value;
    }

    public static int positiveInt(String field, int value) {
        if (value < 1) {
            throw new IllegalArgumentException(field + " MUST be >= 1");
        }
        return value;
    }
}
