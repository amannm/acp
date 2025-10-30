package com.amannmalik.acp.api.checkout.model;

import com.amannmalik.acp.api.shared.CurrencyCode;
import com.amannmalik.acp.util.Ensure;

import java.util.List;

public record CheckoutSession(
        CheckoutSessionId id,
        Buyer buyer,
        PaymentProvider paymentProvider,
        CheckoutSessionStatus status,
        CurrencyCode currency,
        List<LineItem> lineItems,
        Address fulfillmentAddress,
        List<FulfillmentOption> fulfillmentOptions,
        FulfillmentOptionId fulfillmentOptionId,
        List<Total> totals,
        List<Message> messages,
        List<Link> links,
        Order order) {
    public CheckoutSession {
        id = Ensure.notNull("checkout_session.id", id);
        // Optional fields may remain null intentionally.
        paymentProvider = Ensure.notNull("checkout_session.payment_provider", paymentProvider);
        status = Ensure.notNull("checkout_session.status", status);
        currency = Ensure.notNull("checkout_session.currency", currency);
        lineItems = Ensure.immutableList("checkout_session.line_items", lineItems);
        fulfillmentOptions = Ensure.immutableList("checkout_session.fulfillment_options", fulfillmentOptions);
        totals = Ensure.immutableList("checkout_session.totals", totals);
        messages = Ensure.immutableList("checkout_session.messages", messages);
        links = Ensure.immutableList("checkout_session.links", links);
        if (fulfillmentOptions.isEmpty()) {
            throw new IllegalArgumentException("checkout_session.fulfillment_options MUST NOT be empty");
        }
        if (lineItems.isEmpty()) {
            throw new IllegalArgumentException("checkout_session.line_items MUST NOT be empty");
        }
        if (totals.isEmpty()) {
            throw new IllegalArgumentException("checkout_session.totals MUST NOT be empty");
        }
    }
}
