package com.amannmalik.acp.api.delegatepayment;

import com.amannmalik.acp.api.checkout.model.CheckoutSessionId;
import com.amannmalik.acp.api.shared.CurrencyCode;
import com.amannmalik.acp.api.shared.MinorUnitAmount;

public interface DelegatePaymentTokenValidator {
    DelegatePaymentTokenValidator NOOP = (token, checkoutSessionId, totalAmount, currency) -> TokenReservation.NOOP;

    TokenReservation reserve(String token, CheckoutSessionId checkoutSessionId, MinorUnitAmount totalAmount, CurrencyCode currency);

    interface TokenReservation extends AutoCloseable {
        TokenReservation NOOP = new TokenReservation() {
            @Override
            public void commit() {
            }

            @Override
            public void close() {
            }
        };

        void commit();

        @Override
        void close();
    }
}
