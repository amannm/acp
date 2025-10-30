package com.amannmalik.acp.server.webhook;

public interface OrderWebhookPublisher {
    OrderWebhookPublisher NOOP = event -> {};

    void publish(OrderWebhookEvent event);
}
