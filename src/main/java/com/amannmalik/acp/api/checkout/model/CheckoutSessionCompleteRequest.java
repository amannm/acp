package com.amannmalik.acp.api.checkout.model;

import com.amannmalik.acp.util.Ensure;

public record CheckoutSessionCompleteRequest(Buyer buyer, PaymentData paymentData) {
    public CheckoutSessionCompleteRequest {
        paymentData = Ensure.notNull("complete.payment_data", paymentData);
    }
}
