package com.amannmalik.acp.testsuite.checkout;

import com.amannmalik.acp.api.checkout.InMemoryCheckoutSessionService;
import com.amannmalik.acp.api.checkout.model.CheckoutSessionCreateRequest;
import com.amannmalik.acp.api.checkout.model.CheckoutSessionStatus;
import com.amannmalik.acp.api.checkout.model.Item;
import com.amannmalik.acp.api.shared.CurrencyCode;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

final class InMemoryCheckoutSessionServiceTest {
    private static final Clock FIXED_CLOCK = Clock.fixed(Instant.parse("2025-10-25T10:00:00Z"), ZoneOffset.UTC);

    @Test
    void createSessionProducesLineItemsAndTotals() {
        var service = new InMemoryCheckoutSessionService(Map.of("item_test", 1200L), FIXED_CLOCK, new CurrencyCode("usd"));
        var request = new CheckoutSessionCreateRequest(
                List.of(new Item("item_test", 2)),
                null,
                null);

        var session = service.create(request);

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
}
