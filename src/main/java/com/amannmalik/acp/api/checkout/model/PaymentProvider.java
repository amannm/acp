package com.amannmalik.acp.api.checkout.model;

import com.amannmalik.acp.util.Ensure;

import java.util.List;

public record PaymentProvider(Provider provider, List<PaymentMethod> supportedPaymentMethods) {
    public PaymentProvider {
        provider = Ensure.notNull("payment_provider.provider", provider);
        supportedPaymentMethods = Ensure.immutableList("payment_provider.supported_payment_methods", supportedPaymentMethods);
        if (supportedPaymentMethods.isEmpty()) {
            throw new IllegalArgumentException("payment_provider.supported_payment_methods MUST include at least one method");
        }
    }

    public enum Provider {
        STRIPE
    }

    public enum PaymentMethod {
        CARD
    }
}
