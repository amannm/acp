package com.amannmalik.acp.spi.webhook;

public interface OrderWebhookPublisher {
    OrderWebhookPublisher NOOP = event -> {};

    void publish(OrderWebhookEvent event);
}
