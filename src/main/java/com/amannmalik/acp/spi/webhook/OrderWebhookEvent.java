package com.amannmalik.acp.spi.webhook;

import com.amannmalik.acp.api.shared.MinorUnitAmount;
import com.amannmalik.acp.util.Ensure;

import java.net.URI;
import java.util.List;

public record OrderWebhookEvent(
        Type type,
        String checkoutSessionId,
        OrderStatus status,
        URI permalinkUrl,
        List<Refund> refunds) {
    public OrderWebhookEvent {
        type = Ensure.notNull("webhook_event.type", type);
        checkoutSessionId = Ensure.nonBlank("webhook_event.checkout_session_id", checkoutSessionId);
        status = Ensure.notNull("webhook_event.status", status);
        permalinkUrl = Ensure.notNull("webhook_event.permalink_url", permalinkUrl);
        refunds = Ensure.immutableList("webhook_event.refunds", refunds == null ? List.of() : refunds);
    }

    public enum Type {
        ORDER_CREATE("order_create"),
        ORDER_UPDATE("order_update");

        private final String jsonValue;

        Type(String jsonValue) {
            this.jsonValue = jsonValue;
        }

        public String jsonValue() {
            return jsonValue;
        }
    }

    public enum OrderStatus {
        CREATED("created"),
        MANUAL_REVIEW("manual_review"),
        CONFIRMED("confirmed"),
        CANCELED("canceled"),
        SHIPPED("shipped"),
        FULFILLED("fulfilled");

        private final String jsonValue;

        OrderStatus(String jsonValue) {
            this.jsonValue = jsonValue;
        }

        public String jsonValue() {
            return jsonValue;
        }
    }

    public enum RefundType {
        STORE_CREDIT("store_credit"),
        ORIGINAL_PAYMENT("original_payment");

        private final String jsonValue;

        RefundType(String jsonValue) {
            this.jsonValue = jsonValue;
        }

        public String jsonValue() {
            return jsonValue;
        }
    }

    public record Refund(RefundType type, MinorUnitAmount amount) {
        public Refund {
            type = Ensure.notNull("refund.type", type);
            amount = Ensure.notNull("refund.amount", amount);
        }
    }
}
