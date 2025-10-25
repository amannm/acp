package com.amannmalik.acp.api.checkout.model;

import com.amannmalik.acp.util.Ensure;

public record PaymentData(String token, PaymentProvider.Provider provider, Address billingAddress) {
    public PaymentData {
        token = Ensure.nonBlank("payment_data.token", token);
        provider = Ensure.notNull("payment_data.provider", provider);
    }
}
