package com.amannmalik.acp.checkout;

import com.amannmalik.acp.api.checkout.*;
import com.amannmalik.acp.api.checkout.model.*;
import com.amannmalik.acp.api.delegatepayment.DelegatePaymentTokenException;
import com.amannmalik.acp.api.delegatepayment.DelegatePaymentTokenValidator;
import com.amannmalik.acp.api.delegatepayment.DelegatePaymentTokenValidator.TokenReservation;
import com.amannmalik.acp.api.delegatepayment.InMemoryDelegatePaymentService;
import com.amannmalik.acp.api.delegatepayment.model.*;
import com.amannmalik.acp.api.shared.CurrencyCode;
import com.amannmalik.acp.api.shared.MinorUnitAmount;
import com.amannmalik.acp.spi.webhook.OrderWebhookEvent;
import com.amannmalik.acp.spi.webhook.OrderWebhookPublisher;
import org.junit.jupiter.api.Test;

import java.time.*;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

final class InMemoryCheckoutSessionServiceTest {
    private static final CurrencyCode USD = new CurrencyCode("usd");
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2025-11-10T00:00:00Z"), ZoneOffset.UTC);

    private static InMemoryCheckoutSessionService service(OrderWebhookPublisher publisher) {
        return new InMemoryCheckoutSessionService(priceBook(), CLOCK, USD, publisher);
    }

    private static InMemoryCheckoutSessionService service(
            OrderWebhookPublisher publisher, DelegatePaymentTokenValidator tokenValidator) {
        return new InMemoryCheckoutSessionService(priceBook(), CLOCK, USD, publisher, tokenValidator);
    }

    private static Map<String, Long> priceBook() {
        return Map.of(
                "item_123", 1_000L,
                "item_456", 2_000L);
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

    @Test
    void createWithoutAddressIsNotReady() {
        var service = service(new RecordingWebhookPublisher());
        var request = new CheckoutSessionCreateRequest(List.of(new Item("item_123", 1)), null, null);
        var session = service.create(request, "idem-missing-address");
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
        var session = service.create(new CheckoutSessionCreateRequest(List.of(new Item("item_123", 1)), buyer(), address()), "idem-complete-ready");
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
        var session = service.create(new CheckoutSessionCreateRequest(List.of(new Item("item_123", 1)), null, null), "idem-not-ready");
        var completeRequest = new CheckoutSessionCompleteRequest(null, new PaymentData("tok_123", PaymentProvider.Provider.STRIPE, null));
        assertThrows(CheckoutSessionValidationException.class, () -> service.complete(session.id(), completeRequest, "idem"));
    }

    @Test
    void completeFailsWhenTokenValidatorRejects() {
        var service = service(new RecordingWebhookPublisher(), new RejectingTokenValidator());
        var createRequest = new CheckoutSessionCreateRequest(List.of(new Item("item_123", 1)), buyer(), address());
        var session = service.create(createRequest, "idem-token-reject");
        var completeRequest = new CheckoutSessionCompleteRequest(
                buyer(), new PaymentData("tok_invalid", PaymentProvider.Provider.STRIPE, null));
        var exception = assertThrows(
                CheckoutSessionValidationException.class,
                () -> service.complete(session.id(), completeRequest, "complete-reject"));
        assertEquals("invalid_token", exception.code());
        assertEquals(400, exception.status());
        assertEquals("$.payment_data.token", exception.param());
    }

    @Test
    void completeSucceedsWithDelegatedTokenAndCannotBeReused() {
        var publisher = new RecordingWebhookPublisher();
        var delegateService = new InMemoryDelegatePaymentService(CLOCK);
        var service = new InMemoryCheckoutSessionService(priceBook(), CLOCK, USD, publisher, delegateService);
        var session = service.create(
                new CheckoutSessionCreateRequest(List.of(new Item("item_123", 1)), buyer(), address()),
                "idem-token-success");
        var delegateRequest = delegatePaymentRequest(session.id().value(), 10_000L);
        var token = delegateService.create(delegateRequest, "delegate-key").id();
        var completion = new CheckoutSessionCompleteRequest(
                buyer(), new PaymentData(token, PaymentProvider.Provider.STRIPE, null));
        var completed = service.complete(session.id(), completion, "complete-success");
        assertEquals(CheckoutSessionStatus.COMPLETED, completed.status());
        var anotherSession = service.create(
                new CheckoutSessionCreateRequest(List.of(new Item("item_123", 1)), buyer(), address()),
                "idem-token-reuse");
        var reuse = new CheckoutSessionCompleteRequest(
                buyer(), new PaymentData(token, PaymentProvider.Provider.STRIPE, null));
        assertThrows(CheckoutSessionValidationException.class, () -> service.complete(anotherSession.id(), reuse, "complete-reuse"));
    }

    private static final class RecordingWebhookPublisher implements OrderWebhookPublisher {
        private final List<OrderWebhookEvent> events = new ArrayList<>();

        @Override
        public void publish(OrderWebhookEvent event) {
            events.add(event);
        }
    }

    private static DelegatePaymentRequest delegatePaymentRequest(String sessionId, long maxAmount) {
        return new DelegatePaymentRequest(
                paymentMethod(),
                new Allowance(
                        Allowance.Reason.ONE_TIME,
                        new MinorUnitAmount(maxAmount),
                        USD,
                        sessionId,
                        "merchant_ref",
                        CLOCK.instant().plus(Duration.ofDays(1))),
                address(),
                List.of(new RiskSignal(RiskSignal.Type.CARD_TESTING, 10, RiskSignal.Action.AUTHORIZED)),
                Map.of("source", "test"));
    }

    private static PaymentMethodCard paymentMethod() {
        return new PaymentMethodCard(
                PaymentMethodCard.CardNumberType.FPAN,
                false,
                "4242424242424242",
                "11",
                "2026",
                "Jane Doe",
                "123",
                null,
                null,
                List.of(PaymentMethodCard.Check.AVS),
                "424242",
                PaymentMethodCard.DisplayCardFundingType.CREDIT,
                "apple_pay",
                "visa",
                "4242",
                Map.of("issuer", "test"));
    }

    private static final class RejectingTokenValidator implements DelegatePaymentTokenValidator {
        @Override
        public TokenReservation reserve(String token, CheckoutSessionId checkoutSessionId, MinorUnitAmount totalAmount, CurrencyCode currency) {
            throw new DelegatePaymentTokenException("Rejected", "invalid_token", "$.payment_data.token");
        }
    }
}
