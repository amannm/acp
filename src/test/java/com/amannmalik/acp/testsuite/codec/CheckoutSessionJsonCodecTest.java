package com.amannmalik.acp.testsuite.codec;

import com.amannmalik.acp.api.checkout.model.*;
import com.amannmalik.acp.api.shared.*;
import com.amannmalik.acp.codec.CheckoutSessionJsonCodec;
import jakarta.json.Json;
import jakarta.json.JsonObject;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

final class CheckoutSessionJsonCodecTest {
    private static CheckoutSession sampleSession() {
        var sessionId = new CheckoutSessionId("csn_sample");
        var buyer = new Buyer("Ada", "Lovelace", "ada@example.com", "15551234567");
        var paymentProvider = new PaymentProvider(
                PaymentProvider.Provider.STRIPE, List.of(PaymentProvider.PaymentMethod.CARD));
        var lineItem = new LineItem(
                "line_000001",
                new Item("item_123", 1),
                new MinorUnitAmount(500),
                MinorUnitAmount.zero(),
                new MinorUnitAmount(500),
                new MinorUnitAmount(45),
                new MinorUnitAmount(545));
        var shipping = new FulfillmentOption.Shipping(
                "fulfillment_option_standard",
                "Standard",
                "Arrives in 3-5 days",
                "USPS",
                Instant.parse("2025-11-02T12:00:00Z"),
                Instant.parse("2025-11-04T12:00:00Z"),
                new MinorUnitAmount(100),
                MinorUnitAmount.zero(),
                new MinorUnitAmount(100));
        var totals = List.of(
                new Total(Total.TotalType.ITEMS_BASE_AMOUNT, "Item(s) total", new MinorUnitAmount(500)),
                new Total(Total.TotalType.SUBTOTAL, "Subtotal", new MinorUnitAmount(500)),
                new Total(Total.TotalType.TAX, "Tax", new MinorUnitAmount(45)),
                new Total(Total.TotalType.FULFILLMENT, "Fulfillment", new MinorUnitAmount(100)),
                new Total(Total.TotalType.TOTAL, "Total", new MinorUnitAmount(645)));
        var links = List.of(new Link(
                Link.LinkType.TERMS_OF_USE, URI.create("https://merchant.example.com/legal/terms-of-use")));
        var messages = List.<Message>of(
                new Message.Info("$.line_items[0]", Message.ContentType.PLAIN, "Ready to confirm."));
        var order = new Order("ord_000111", sessionId, URI.create("https://merchant.example.com/orders/ord_000111"));
        return new CheckoutSession(
                sessionId,
                buyer,
                paymentProvider,
                CheckoutSessionStatus.READY_FOR_PAYMENT,
                new CurrencyCode("usd"),
                List.of(lineItem),
                new Address(
                        "Ada Lovelace",
                        "1 Market St",
                        "Suite 200",
                        "San Francisco",
                        "CA",
                        "US",
                        "94105"),
                List.of(shipping),
                new FulfillmentOptionId(shipping.id()),
                totals,
                messages,
                links,
                order);
    }

    private static JsonObject readJson(ByteArrayOutputStream output) {
        try (var reader = Json.createReader(new ByteArrayInputStream(output.toByteArray()))) {
            return reader.readObject();
        }
    }

    private static ByteArrayInputStream input(String json) {
        return new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void writeCheckoutSessionProducesSpecShape() {
        var codec = new CheckoutSessionJsonCodec();
        var output = new ByteArrayOutputStream();

        codec.writeCheckoutSession(output, sampleSession());

        var json = readJson(output);
        assertEquals("csn_sample", json.getString("id"));
        assertEquals("ready_for_payment", json.getString("status"));
        assertEquals("usd", json.getString("currency"));

        var paymentProvider = json.getJsonObject("payment_provider");
        assertEquals("stripe", paymentProvider.getString("provider"));
        assertEquals(List.of("card"), paymentProvider.getJsonArray("supported_payment_methods").stream()
                .map(value -> value.toString().replace("\"", ""))
                .toList());

        var lineItems = json.getJsonArray("line_items");
        assertEquals(1, lineItems.size());
        var lineItem = lineItems.getJsonObject(0);
        assertEquals(545, lineItem.getInt("total"));
        assertEquals(1, lineItem.getJsonObject("item").getInt("quantity"));

        var fulfillmentOptions = json.getJsonArray("fulfillment_options");
        assertEquals(1, fulfillmentOptions.size());
        var option = fulfillmentOptions.getJsonObject(0);
        assertEquals("shipping", option.getString("type"));
        assertEquals("fulfillment_option_standard", option.getString("id"));
        assertEquals(100, option.getInt("total"));
        assertTrue(option.containsKey("earliest_delivery_time"));

        var totals = json.getJsonArray("totals");
        assertEquals(5, totals.size());
        assertEquals("total", totals.getJsonObject(4).getString("type"));

        var messages = json.getJsonArray("messages");
        assertEquals(1, messages.size());
        var message = messages.getJsonObject(0);
        assertEquals("info", message.getString("type"));
        assertEquals("$.line_items[0]", message.getString("param"));

        var links = json.getJsonArray("links");
        assertEquals(1, links.size());
        assertEquals("terms_of_use", links.getJsonObject(0).getString("type"));

        var order = json.getJsonObject("order");
        assertNotNull(order);
        assertEquals("ord_000111", order.getString("id"));
        assertEquals("csn_sample", order.getString("checkout_session_id"));
    }

    @Test
    void writeErrorProducesFlatStructure() {
        var codec = new CheckoutSessionJsonCodec();
        var output = new ByteArrayOutputStream();
        var error = new ErrorResponse(
                ErrorResponse.ErrorType.INVALID_REQUEST, "invalid", "Bad input", "$.items[0]");

        codec.writeError(output, error);

        var json = readJson(output);
        assertEquals("invalid_request", json.getString("type"));
        assertEquals("invalid", json.getString("code"));
        assertEquals("Bad input", json.getString("message"));
        assertEquals("$.items[0]", json.getString("param"));
    }

    @Test
    void readRequestsMapJsonToModels() {
        var codec = new CheckoutSessionJsonCodec();
        var createJson = """
                {
                  "items": [{"id": "item_123", "quantity": 2}],
                  "buyer": {
                    "first_name": "Ada",
                    "last_name": "Lovelace",
                    "email": "ada@example.com"
                  },
                  "fulfillment_address": {
                    "name": "Ada Lovelace",
                    "line_one": "1 Market St",
                    "city": "San Francisco",
                    "state": "CA",
                    "country": "US",
                    "postal_code": "94105"
                  }
                }
                """;
        var updateJson = """
                {
                  "items": [{"id": "item_123", "quantity": 1}],
                  "fulfillment_option_id": "fulfillment_option_standard"
                }
                """;
        var completeJson = """
                {
                  "buyer": {
                    "first_name": "Ada",
                    "last_name": "Lovelace",
                    "email": "ada@example.com"
                  },
                  "payment_data": {
                    "token": "tok_sample",
                    "provider": "stripe"
                  }
                }
                """;

        CheckoutSessionCreateRequest createRequest = codec.readCreateRequest(input(createJson));
        assertEquals(1, createRequest.items().size());
        assertEquals(2, createRequest.items().getFirst().quantity());
        assertNotNull(createRequest.buyer());
        assertEquals("San Francisco", createRequest.fulfillmentAddress().city());

        CheckoutSessionUpdateRequest updateRequest = codec.readUpdateRequest(input(updateJson));
        assertEquals(1, updateRequest.items().size());
        assertEquals("fulfillment_option_standard", updateRequest.fulfillmentOptionId().value());

        CheckoutSessionCompleteRequest completeRequest = codec.readCompleteRequest(input(completeJson));
        assertEquals("tok_sample", completeRequest.paymentData().token());
        assertEquals(PaymentProvider.Provider.STRIPE, completeRequest.paymentData().provider());
    }
}
