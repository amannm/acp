package com.amannmalik.acp.spi.webhook;

import com.amannmalik.acp.util.Ensure;
import java.net.URI;

public record OrderWebhookEvent(Type type, String checkoutSessionId, String status, URI permalinkUrl) {
    public OrderWebhookEvent {
        type = Ensure.notNull("webhook_event.type", type);
        checkoutSessionId = Ensure.nonBlank("webhook_event.checkout_session_id", checkoutSessionId);
        status = Ensure.nonBlank("webhook_event.status", status);
        permalinkUrl = Ensure.notNull("webhook_event.permalink_url", permalinkUrl);
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
}
