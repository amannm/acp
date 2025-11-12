package com.amannmalik.acp.testsuite.codec;

import com.amannmalik.acp.api.checkout.model.CheckoutSession;
import com.amannmalik.acp.api.delegatepayment.model.DelegatePaymentResponse;
import com.amannmalik.acp.codec.CheckoutSessionJsonCodec;
import com.amannmalik.acp.codec.DelegatePaymentJsonCodec;
import jakarta.json.Json;
import jakarta.json.JsonObject;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

final class SpecificationExamplesTest {
    private static final Path CHECKOUT_EXAMPLES =
            Path.of("specification/2025-09-29/examples/examples.agentic_checkout.json");
    private static final Path DELEGATE_EXAMPLES =
            Path.of("specification/2025-09-29/examples/examples.delegate_payment.json");

    @Test
    void checkoutExamplesAreAcceptedAndProduced() throws IOException {
        var codec = new CheckoutSessionJsonCodec();
        var examples = readExamples(CHECKOUT_EXAMPLES);

        var createExample = examples.getJsonObject("create_checkout_session_request");
        var createRequest = codec.readCreateRequest(toInput(createExample));
        assertEquals(1, createRequest.items().size());
        assertEquals("item_123", createRequest.items().getFirst().id());
        assertNotNull(createRequest.fulfillmentAddress());
        assertEquals("San Francisco", createRequest.fulfillmentAddress().city());

        var responseExample = examples.getJsonObject("create_checkout_session_response");
        CheckoutSession session = codec.readCheckoutSession(toInput(responseExample));
        assertEquals("checkout_session_123", session.id().value());
        assertEquals("ready_for_payment", session.status().jsonValue());
        assertEquals("usd", session.currency().value());
        assertEquals(2, session.fulfillmentOptions().size());
        assertEquals(5, session.totals().size());

        var output = new ByteArrayOutputStream();
        codec.writeCheckoutSession(output, session);
        var roundTrip = readJson(output);
        assertEquals(session.id().value(), roundTrip.getString("id"));
        assertEquals(responseExample.getJsonArray("line_items").size(), roundTrip.getJsonArray("line_items").size());
        assertEquals(responseExample.getJsonArray("totals").size(), roundTrip.getJsonArray("totals").size());
        assertEquals(responseExample.getJsonArray("fulfillment_options").size(),
                roundTrip.getJsonArray("fulfillment_options").size());
    }

    @Test
    void delegateExamplesAreAcceptedAndProduced() throws IOException {
        var codec = new DelegatePaymentJsonCodec();
        var examples = readExamples(DELEGATE_EXAMPLES);

        var requestExample = examples.getJsonObject("delegate_payment_request");
        var request = codec.readRequest(toInput(requestExample));
        assertEquals("4242424242424242", request.paymentMethod().number());
        assertEquals("usd", request.allowance().currency().value());
        assertEquals(2000L, request.allowance().maxAmount().value());
        assertEquals(1, request.riskSignals().size());
        assertEquals("card_testing", request.riskSignals().getFirst().type().name().toLowerCase());
        assertEquals("q4", request.metadata().get("campaign"));
        assertEquals("acme_store", request.allowance().merchantId());

        var responseExample = examples.getJsonObject("delegate_payment_success_response");
        var response = new DelegatePaymentResponse(
                responseExample.getString("id"),
                Instant.parse(responseExample.getString("created")),
                toMetadata(responseExample.getJsonObject("metadata")));

        var output = new ByteArrayOutputStream();
        codec.writeResponse(output, response);
        var json = readJson(output);
        assertEquals(responseExample.getString("id"), json.getString("id"));
        assertEquals(responseExample.getString("created"), json.getString("created"));
        assertEquals(responseExample.getJsonObject("metadata"), json.getJsonObject("metadata"));
    }

    private static JsonObject readExamples(Path path) throws IOException {
        try (var reader = Json.createReader(Files.newBufferedReader(path))) {
            return reader.readObject();
        }
    }

    private static ByteArrayInputStream toInput(JsonObject object) {
        return new ByteArrayInputStream(object.toString().getBytes(StandardCharsets.UTF_8));
    }

    private static JsonObject readJson(ByteArrayOutputStream output) {
        try (var reader = Json.createReader(new ByteArrayInputStream(output.toByteArray()))) {
            return reader.readObject();
        }
    }

    private static Map<String, String> toMetadata(JsonObject object) {
        var map = new LinkedHashMap<String, String>();
        object.forEach((key, value) -> map.put(key, object.getString(key)));
        return Map.copyOf(map);
    }
}
