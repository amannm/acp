package com.amannmalik.acp.api.checkout.tests;

import com.amannmalik.acp.api.checkout.*;
import com.amannmalik.acp.api.checkout.model.*;
import com.amannmalik.acp.api.shared.CurrencyCode;
import com.amannmalik.acp.spi.webhook.OrderWebhookEvent;
import com.amannmalik.acp.spi.webhook.OrderWebhookPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

final class InMemoryCheckoutSessionServiceTest {
    private static final Clock FIXED_CLOCK = Clock.fixed(Instant.parse("2025-10-31T00:00:00Z"), ZoneOffset.UTC);
    private CapturingWebhookPublisher webhookPublisher;
    private InMemoryCheckoutSessionService service;

    @BeforeEach
    void setUp() {
        webhookPublisher = new CapturingWebhookPublisher();
        service = new InMemoryCheckoutSessionService(
                Map.of("item_123", 1500L),
                FIXED_CLOCK,
                new CurrencyCode("usd"),
                webhookPublisher);
    }

    @Test
    void createHonorsIdempotency() {
        var request = createRequest();
        var first = service.create(request, "idem_create");
        var second = service.create(request, "idem_create");
        assertEquals(first, second);

        var conflictRequest = new CheckoutSessionCreateRequest(
                List.of(new Item("item_123", 2)),
                request.buyer(),
                request.fulfillmentAddress());
        assertThrows(
                CheckoutSessionIdempotencyConflictException.class,
                () -> service.create(conflictRequest, "idem_create"));
    }

    @Test
    void completePublishesWebhooksAndHonorsIdempotency() {
        var createRequest = createRequest();
        var session = service.create(createRequest, "idem_create");
        var completion = new CheckoutSessionCompleteRequest(
                session.buyer(),
                new PaymentData("token_123", PaymentProvider.Provider.STRIPE, billingAddress()));

        var completed = service.complete(session.id(), completion, "idem_complete");
        assertEquals(CheckoutSessionStatus.COMPLETED, completed.status());
        assertNotNull(completed.order());
        assertEquals(2, webhookPublisher.events.size());

        var createEvent = webhookPublisher.events.get(0);
        assertEquals(OrderWebhookEvent.Type.ORDER_CREATE, createEvent.type());
        assertEquals(OrderWebhookEvent.OrderStatus.CREATED, createEvent.status());
        assertEquals(session.id().value(), createEvent.checkoutSessionId());
        assertEquals(URI.create("https://merchant.example.com/orders/" + completed.order().id()), createEvent.permalinkUrl());

        var updateEvent = webhookPublisher.events.get(1);
        assertEquals(OrderWebhookEvent.Type.ORDER_UPDATE, updateEvent.type());
        assertEquals(OrderWebhookEvent.OrderStatus.CONFIRMED, updateEvent.status());
        assertTrue(updateEvent.refunds().isEmpty());

        var repeated = service.complete(session.id(), completion, "idem_complete");
        assertEquals(completed, repeated);
        assertEquals(2, webhookPublisher.events.size(), "Idempotent complete should not emit extra events");

        var differentPayment = new CheckoutSessionCompleteRequest(
                session.buyer(),
                new PaymentData("different_token", PaymentProvider.Provider.STRIPE, billingAddress()));
        assertThrows(
                CheckoutSessionIdempotencyConflictException.class,
                () -> service.complete(session.id(), differentPayment, "idem_complete"));
    }

    @Test
    void createWithoutFulfillmentAddressIsNotReady() {
        var request = new CheckoutSessionCreateRequest(
                List.of(new Item("item_123", 1)),
                new Buyer("Jane", "Doe", "jane@example.com", null),
                null);

        var session = service.create(request, null);

        assertEquals(CheckoutSessionStatus.NOT_READY_FOR_PAYMENT, session.status());
        assertFalse(session.messages().isEmpty());
        assertTrue(session.messages().get(0) instanceof Message.Info info
                && "$.fulfillment_address".equals(info.param()));
    }

    @Test
    void selectingDigitalFulfillmentOptionRemovesAddressRequirement() {
        var createRequest = new CheckoutSessionCreateRequest(
                List.of(new Item("item_123", 1)),
                new Buyer("Alex", "Doe", "alex@example.com", null),
                null);

        var session = service.create(createRequest, null);
        assertEquals(CheckoutSessionStatus.NOT_READY_FOR_PAYMENT, session.status());

        var updateRequest = new CheckoutSessionUpdateRequest(
                null,
                null,
                null,
                new FulfillmentOptionId("fulfillment_option_digital"));

        var updated = service.update(session.id(), updateRequest);

        assertEquals(CheckoutSessionStatus.READY_FOR_PAYMENT, updated.status());
        assertTrue(updated.messages().isEmpty());
        assertEquals("fulfillment_option_digital", updated.fulfillmentOptionId().value());
    }

    @Test
    void createWithUnknownItemFailsFast() {
        var request = new CheckoutSessionCreateRequest(
                List.of(new Item("unknown_item", 1)),
                new Buyer("John", "Doe", "john@example.com", null),
                fulfillmentAddress());

        var exception = assertThrows(
                CheckoutSessionValidationException.class, () -> service.create(request, null));
        assertEquals("unknown_item", exception.code());
        assertEquals("$.items[0].id", exception.param());
        assertEquals(400, exception.status());
    }

    @Test
    void createUsesConfiguredCurrency() {
        var eurService = new InMemoryCheckoutSessionService(
                Map.of("item_123", 1500L),
                FIXED_CLOCK,
                new CurrencyCode("eur"),
                webhookPublisher);

        var session = eurService.create(createRequest(), null);

        assertEquals("eur", session.currency().value());
    }

    private CheckoutSessionCreateRequest createRequest() {
        return new CheckoutSessionCreateRequest(
                List.of(new Item("item_123", 1)),
                new Buyer("John", "Doe", "john@example.com", null),
                fulfillmentAddress());
    }

    private Address fulfillmentAddress() {
        return new Address(
                "John Doe",
                "1234 Chat Road",
                "",
                "San Francisco",
                "CA",
                "US",
                "94131");
    }

    private Address billingAddress() {
        return new Address(
                "John Doe",
                "1234 Chat Road",
                "",
                "San Francisco",
                "CA",
                "US",
                "94131");
    }

    private static final class CapturingWebhookPublisher implements OrderWebhookPublisher {
        private final List<OrderWebhookEvent> events = new ArrayList<>();

        @Override
        public void publish(OrderWebhookEvent event) {
            events.add(event);
        }
    }
}
