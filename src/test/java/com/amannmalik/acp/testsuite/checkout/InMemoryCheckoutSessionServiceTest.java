package com.amannmalik.acp.testsuite.checkout;

import com.amannmalik.acp.api.checkout.CheckoutSessionIdempotencyConflictException;
import com.amannmalik.acp.api.checkout.InMemoryCheckoutSessionService;
import com.amannmalik.acp.api.checkout.model.CheckoutSessionCompleteRequest;
import com.amannmalik.acp.api.checkout.model.CheckoutSessionCreateRequest;
import com.amannmalik.acp.api.checkout.model.CheckoutSessionStatus;
import com.amannmalik.acp.api.checkout.model.CheckoutSessionUpdateRequest;
import com.amannmalik.acp.api.checkout.model.Item;
import com.amannmalik.acp.api.checkout.model.PaymentData;
import com.amannmalik.acp.api.checkout.model.PaymentProvider;
import com.amannmalik.acp.api.shared.CurrencyCode;
import com.amannmalik.acp.spi.webhook.OrderWebhookEvent;
import com.amannmalik.acp.spi.webhook.OrderWebhookPublisher;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class InMemoryCheckoutSessionServiceTest {
    private static final Clock FIXED_CLOCK = Clock.fixed(Instant.parse("2025-10-25T10:00:00Z"), ZoneOffset.UTC);

    @Test
    void createSessionProducesLineItemsAndTotals() {
        var service = new InMemoryCheckoutSessionService(Map.of("item_test", 1200L), FIXED_CLOCK, new CurrencyCode("usd"));
        var request = new CheckoutSessionCreateRequest(
                List.of(new Item("item_test", 2)),
                null,
                null);

        var session = service.create(request, null);

        assertEquals(CheckoutSessionStatus.READY_FOR_PAYMENT, session.status());
        assertEquals("usd", session.currency().value());
        assertEquals(1, session.lineItems().size());
        var lineItem = session.lineItems().get(0);
        assertEquals(2, lineItem.item().quantity());
        assertEquals(2400L, lineItem.baseAmount().value());
        assertEquals(0L, lineItem.discount().value());
        assertNotNull(session.fulfillmentOptionId());
        assertEquals(6, session.totals().size());
    }

    @Test
    void createRejectsConflictingIdempotencyRequest() {
        var service = new InMemoryCheckoutSessionService(
                Map.of("item_test", 1200L), FIXED_CLOCK, new CurrencyCode("usd"), OrderWebhookPublisher.NOOP);
        var baseRequest = new CheckoutSessionCreateRequest(List.of(new Item("item_test", 1)), null, null);
        var conflictRequest = new CheckoutSessionCreateRequest(List.of(new Item("item_test", 2)), null, null);

        service.create(baseRequest, "idem-1");

        assertThrows(CheckoutSessionIdempotencyConflictException.class, () -> service.create(conflictRequest, "idem-1"));
    }

    @Test
    void completePublishesOrderLifecycleEvents() {
        var events = new ArrayList<OrderWebhookEvent>();
        var publisher = new RecordingPublisher(events);
        var service = new InMemoryCheckoutSessionService(
                Map.of("item_test", 1200L), FIXED_CLOCK, new CurrencyCode("usd"), publisher);

        var session = service.create(new CheckoutSessionCreateRequest(List.of(new Item("item_test", 1)), null, null), null);
        var completeRequest = new CheckoutSessionCompleteRequest(null, new PaymentData("tok", PaymentProvider.Provider.STRIPE, null));

        service.complete(session.id(), completeRequest, "complete-1");

        assertEquals(2, events.size());

        var createEvent = events.get(0);
        assertEquals(OrderWebhookEvent.Type.ORDER_CREATE, createEvent.type());
        assertEquals(session.id().value(), createEvent.checkoutSessionId());
        assertEquals(OrderWebhookEvent.OrderStatus.CREATED, createEvent.status());
        assertTrue(createEvent.permalinkUrl().toString().contains("/orders/"));
        assertTrue(createEvent.refunds().isEmpty());

        var updateEvent = events.get(1);
        assertEquals(OrderWebhookEvent.Type.ORDER_UPDATE, updateEvent.type());
        assertEquals(OrderWebhookEvent.OrderStatus.CONFIRMED, updateEvent.status());
        assertEquals(session.id().value(), updateEvent.checkoutSessionId());
        assertTrue(updateEvent.refunds().isEmpty());
    }

    @Test
    void createIdempotencyReturnsOriginalSnapshotAfterUpdate() {
        var service = new InMemoryCheckoutSessionService(
                Map.of("item_test", 1200L), FIXED_CLOCK, new CurrencyCode("usd"), OrderWebhookPublisher.NOOP);
        var request = new CheckoutSessionCreateRequest(List.of(new Item("item_test", 1)), null, null);
        var original = service.create(request, "idem-keep-orig");

        var updateRequest = new CheckoutSessionUpdateRequest(
                List.of(new Item("item_test", 2)), null, null, original.fulfillmentOptionId());
        var updated = service.update(original.id(), updateRequest);
        assertNotEquals(original, updated);

        var replay = service.create(request, "idem-keep-orig");
        assertEquals(original, replay);
        assertEquals(updated, service.retrieve(original.id()));
    }

    private record RecordingPublisher(List<OrderWebhookEvent> events) implements OrderWebhookPublisher {
        @Override
        public void publish(OrderWebhookEvent event) {
            events.add(event);
        }
    }
}
