package com.amannmalik.acp.api.shared;

public record MinorUnitAmount(long value) {
    private static final long MAX_ABS = 9_000_000_000_000L; // ~USD 90B

    public MinorUnitAmount {
        if (Math.abs(value) > MAX_ABS) {
            throw new IllegalArgumentException("Minor unit amount exceeds safe bounds");
        }
    }

    public static MinorUnitAmount zero() {
        return new MinorUnitAmount(0L);
    }
}
