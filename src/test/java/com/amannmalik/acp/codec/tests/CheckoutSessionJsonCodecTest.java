package com.amannmalik.acp.codec.tests;

import com.amannmalik.acp.codec.CheckoutSessionJsonCodec;
import com.amannmalik.acp.api.checkout.model.*;
import com.amannmalik.acp.api.shared.CurrencyCode;
import com.amannmalik.acp.api.shared.MinorUnitAmount;
import org.junit.jupiter.api.Test;

import jakarta.json.Json;
import jakarta.json.JsonObject;

import java.io.*;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

final class CheckoutSessionJsonCodecTest {
    @Test
    void readCreateRequest_allowsBlankAddressLineTwo() throws Exception {
        var examples = readExamples("specification/2025-09-29/examples/examples.agentic_checkout.json");
        var requestJson = examples.getJsonObject("create_checkout_session_request");
        var codec = new CheckoutSessionJsonCodec();
        var request = codec.readCreateRequest(jsonBytes(requestJson));
        assertNotNull(request.fulfillmentAddress());
        assertEquals("", request.fulfillmentAddress().lineTwo());
    }

    @Test
    void writeCheckoutSession_preservesBlankAddressLineTwo() throws Exception {
        var codec = new CheckoutSessionJsonCodec();
        var session = sampleSession();
        var output = new ByteArrayOutputStream();
        codec.writeCheckoutSession(output, session);
        var json = Json.createReader(new ByteArrayInputStream(output.toByteArray())).readObject();
        var address = json.getJsonObject("fulfillment_address");
        assertTrue(address.containsKey("line_two"), "line_two should be present");
        assertEquals("", address.getString("line_two"));
    }

    private static CheckoutSession sampleSession() {
        var sessionId = new CheckoutSessionId("checkout_session_123");
        var buyer = new Buyer("John", "Doe", "john@example.com", null);
        var paymentProvider = new PaymentProvider(
                PaymentProvider.Provider.STRIPE, List.of(PaymentProvider.PaymentMethod.CARD));
        var lineItem = new LineItem(
                "line_item_123",
                new Item("item_123", 1),
                new MinorUnitAmount(300),
                MinorUnitAmount.zero(),
                new MinorUnitAmount(300),
                new MinorUnitAmount(30),
                new MinorUnitAmount(330));
        var now = Instant.parse("2025-10-09T07:20:50.52Z");
        List<FulfillmentOption> fulfillmentOptions = List.of(
                new FulfillmentOption.Shipping(
                        "fulfillment_option_123",
                        "Standard",
                        "Arrives in 4-5 days",
                        "USPS",
                        now.plusSeconds(3 * 24 * 60 * 60),
                        now.plusSeconds(4 * 24 * 60 * 60),
                        new MinorUnitAmount(100),
                        MinorUnitAmount.zero(),
                        new MinorUnitAmount(100)));
        var totals = List.of(
                new Total(Total.TotalType.ITEMS_BASE_AMOUNT, "Item(s) total", new MinorUnitAmount(300)),
                new Total(Total.TotalType.SUBTOTAL, "Subtotal", new MinorUnitAmount(300)),
                new Total(Total.TotalType.TAX, "Tax", new MinorUnitAmount(30)),
                new Total(Total.TotalType.FULFILLMENT, "Fulfillment", new MinorUnitAmount(100)),
                new Total(Total.TotalType.TOTAL, "Total", new MinorUnitAmount(430)));
        var address = new Address(
                "John Doe",
                "1234 Chat Road,",
                "",
                "San Francisco",
                "CA",
                "US",
                "94131");
        return new CheckoutSession(
                sessionId,
                buyer,
                paymentProvider,
                CheckoutSessionStatus.READY_FOR_PAYMENT,
                new CurrencyCode("usd"),
                List.of(lineItem),
                address,
                fulfillmentOptions,
                new FulfillmentOptionId("fulfillment_option_123"),
                totals,
                List.of(),
                List.of(new Link(Link.LinkType.TERMS_OF_USE, URI.create("https://merchant.example.com/legal/terms-of-use"))),
                null);
    }

    private static InputStream jsonBytes(JsonObject object) {
        return new ByteArrayInputStream(object.toString().getBytes(StandardCharsets.UTF_8));
    }

    private static JsonObject readExamples(String path) throws IOException {
        try (var reader = Json.createReader(Files.newBufferedReader(Path.of(path)))) {
            return reader.readObject();
        }
    }
}
