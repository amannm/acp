package com.amannmalik.acp.checkout;

import com.amannmalik.acp.api.checkout.InMemoryCheckoutSessionService;
import com.amannmalik.acp.api.checkout.CheckoutSessionIdempotencyConflictException;
import com.amannmalik.acp.api.checkout.CheckoutSessionValidationException;
import com.amannmalik.acp.api.checkout.model.*;
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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class InMemoryCheckoutSessionServiceTest {
    private static final CurrencyCode USD = new CurrencyCode("usd");
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2025-11-10T00:00:00Z"), ZoneOffset.UTC);

    @Test
    void createWithoutAddressIsNotReady() {
        var service = service(new RecordingWebhookPublisher());
        var request = new CheckoutSessionCreateRequest(List.of(new Item("item_123", 1)), null, null);
        var session = service.create(request, null);
        assertEquals(CheckoutSessionStatus.NOT_READY_FOR_PAYMENT, session.status());
        assertTrue(session.messages().stream().anyMatch(message -> message.content().contains("fulfillment_address")));
    }

    @Test
    void createWithAddressIsReady() {
        var service = service(new RecordingWebhookPublisher());
        var request = new CheckoutSessionCreateRequest(List.of(new Item("item_123", 1)), buyer(), address());
        var session = service.create(request, "idem-create");
        assertEquals(CheckoutSessionStatus.READY_FOR_PAYMENT, session.status());
        assertNotNull(session.fulfillmentOptionId());
        assertTrue(session.messages().isEmpty());
    }

    @Test
    void createIdempotencyProtectsAgainstConflictingRequests() {
        var service = service(new RecordingWebhookPublisher());
        var first = new CheckoutSessionCreateRequest(List.of(new Item("item_123", 1)), buyer(), address());
        var second = new CheckoutSessionCreateRequest(List.of(new Item("item_456", 1)), buyer(), address());
        service.create(first, "create-key");
        assertThrows(CheckoutSessionIdempotencyConflictException.class, () -> service.create(second, "create-key"));
    }

    @Test
    void completeEmitsWebhooksAndIsIdempotent() {
        var publisher = new RecordingWebhookPublisher();
        var service = service(publisher);
        var session = service.create(new CheckoutSessionCreateRequest(List.of(new Item("item_123", 1)), buyer(), address()), null);
        var completeRequest = new CheckoutSessionCompleteRequest(buyer(), new PaymentData("tok_123", PaymentProvider.Provider.STRIPE, null));
        var completed = service.complete(session.id(), completeRequest, "complete-key");
        assertEquals(CheckoutSessionStatus.COMPLETED, completed.status());
        assertNotNull(completed.order());
        assertEquals(2, publisher.events.size());
        var again = service.complete(session.id(), completeRequest, "complete-key");
        assertEquals(completed.order().id(), again.order().id());
        assertEquals(2, publisher.events.size(), "idempotent completion should not emit duplicate webhooks");
    }

    @Test
    void completeFailsWhenSessionNotReady() {
        var service = service(new RecordingWebhookPublisher());
        var session = service.create(new CheckoutSessionCreateRequest(List.of(new Item("item_123", 1)), null, null), null);
        var completeRequest = new CheckoutSessionCompleteRequest(null, new PaymentData("tok_123", PaymentProvider.Provider.STRIPE, null));
        assertThrows(CheckoutSessionValidationException.class, () -> service.complete(session.id(), completeRequest, "idem"));
    }

    private static InMemoryCheckoutSessionService service(OrderWebhookPublisher publisher) {
        var priceBook = Map.of(
                "item_123", 1_000L,
                "item_456", 2_000L);
        return new InMemoryCheckoutSessionService(priceBook, CLOCK, USD, publisher);
    }

    private static Buyer buyer() {
        return new Buyer("Jane", "Doe", "jane@example.com", "15555555555");
    }

    private static Address address() {
        return new Address(
                "Jane Doe",
                "1234 Chat Road",
                null,
                "San Francisco",
                "CA",
                "US",
                "94131");
    }

    private static final class RecordingWebhookPublisher implements OrderWebhookPublisher {
        private final List<OrderWebhookEvent> events = new ArrayList<>();

        @Override
        public void publish(OrderWebhookEvent event) {
            events.add(event);
        }
    }
}
