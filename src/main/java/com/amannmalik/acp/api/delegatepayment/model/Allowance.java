package com.amannmalik.acp.api.delegatepayment.model;

import com.amannmalik.acp.api.shared.CurrencyCode;
import com.amannmalik.acp.api.shared.MinorUnitAmount;
import com.amannmalik.acp.util.Ensure;

import java.time.Instant;

public record Allowance(
        Reason reason,
        MinorUnitAmount maxAmount,
        CurrencyCode currency,
        String checkoutSessionId,
        String merchantId,
        Instant expiresAt) {
    public Allowance {
        reason = Ensure.notNull("allowance.reason", reason);
        maxAmount = Ensure.notNull("allowance.max_amount", maxAmount);
        currency = Ensure.notNull("allowance.currency", currency);
        checkoutSessionId = Ensure.nonBlank("allowance.checkout_session_id", checkoutSessionId);
        merchantId = Ensure.nonBlank("allowance.merchant_id", merchantId);
        expiresAt = Ensure.notNull("allowance.expires_at", expiresAt);
    }

    public enum Reason {
        ONE_TIME
    }
}
