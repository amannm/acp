package com.amannmalik.acp.delegate;

import com.amannmalik.acp.api.checkout.model.Address;
import com.amannmalik.acp.api.delegatepayment.DelegatePaymentIdempotencyConflictException;
import com.amannmalik.acp.api.delegatepayment.DelegatePaymentValidationException;
import com.amannmalik.acp.api.delegatepayment.InMemoryDelegatePaymentService;
import com.amannmalik.acp.api.delegatepayment.model.*;
import com.amannmalik.acp.api.shared.CurrencyCode;
import com.amannmalik.acp.api.shared.MinorUnitAmount;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

final class InMemoryDelegatePaymentServiceTest {
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2025-11-10T00:00:00Z"), ZoneOffset.UTC);

    @Test
    void idempotentCreateReturnsSameToken() {
        var service = new InMemoryDelegatePaymentService(CLOCK);
        var request = request(Instant.parse("2025-11-11T00:00:00Z"), 2_000L);
        var first = service.create(request, "dp-key");
        var second = service.create(request, "dp-key");
        assertEquals(first.id(), second.id());
        assertEquals(first.metadata(), second.metadata());
    }

    @Test
    void conflictingIdempotentRequestsAreRejected() {
        var service = new InMemoryDelegatePaymentService(CLOCK);
        var request = request(Instant.parse("2025-11-11T00:00:00Z"), 2_000L);
        var changed = request(Instant.parse("2025-11-11T00:00:00Z"), 4_000L);
        service.create(request, "dp-key");
        assertThrows(DelegatePaymentIdempotencyConflictException.class, () -> service.create(changed, "dp-key"));
    }

    @Test
    void allowanceValidationRequiresPositiveAmountAndFutureExpiry() {
        var service = new InMemoryDelegatePaymentService(CLOCK);
        var pastExpiry = Instant.parse("2025-11-09T00:00:00Z");
        assertThrows(
                DelegatePaymentValidationException.class,
                () -> service.create(request(pastExpiry, 2_000L), null));
        assertThrows(
                DelegatePaymentValidationException.class,
                () -> service.create(request(Instant.parse("2025-11-11T00:00:00Z"), 0L), null));
    }

    private static DelegatePaymentRequest request(Instant expiresAt, long maxAmount) {
        return new DelegatePaymentRequest(
                paymentMethod(),
                new Allowance(
                        Allowance.Reason.ONE_TIME,
                        new MinorUnitAmount(maxAmount),
                        new CurrencyCode("usd"),
                        "csn_123",
                        "merchant",
                        expiresAt),
                billingAddress(),
                List.of(new RiskSignal(RiskSignal.Type.CARD_TESTING, 10, RiskSignal.Action.MANUAL_REVIEW)),
                Map.of("campaign", "test"));
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

    private static Address billingAddress() {
        return new Address(
                "Jane Doe",
                "1234 Chat Road",
                null,
                "San Francisco",
                "CA",
                "US",
                "94131");
    }
}
