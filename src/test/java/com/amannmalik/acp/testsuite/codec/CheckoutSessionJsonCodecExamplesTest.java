package com.amannmalik.acp.testsuite.codec;

import com.amannmalik.acp.api.checkout.model.Address;
import com.amannmalik.acp.api.checkout.model.CheckoutSessionCompleteRequest;
import com.amannmalik.acp.api.checkout.model.CheckoutSessionCreateRequest;
import com.amannmalik.acp.api.checkout.model.CheckoutSessionUpdateRequest;
import com.amannmalik.acp.api.checkout.model.Item;
import com.amannmalik.acp.api.checkout.model.PaymentData;
import com.amannmalik.acp.api.checkout.model.PaymentProvider;
import com.amannmalik.acp.codec.CheckoutSessionJsonCodec;
import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonValue;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

final class CheckoutSessionJsonCodecExamplesTest {
    private static JsonObject examples;
    private final CheckoutSessionJsonCodec codec = new CheckoutSessionJsonCodec();

    @BeforeAll
    static void loadExamples() throws Exception {
        try (var reader = Json.createReader(Files.newInputStream(examplePath("examples.agentic_checkout.json")))) {
            examples = reader.readObject();
        }
    }

    @Test
    void createRequestMatchesExampleFixture() {
        var requestJson = examples.getJsonObject("create_checkout_session_request");

        CheckoutSessionCreateRequest request = codec.readCreateRequest(asStream(requestJson));

        List<Item> items = request.items();
        assertEquals(1, items.size());
        assertEquals("item_123", items.get(0).id());
        assertEquals(1, items.get(0).quantity());
        Address address = request.fulfillmentAddress();
        assertNotNull(address);
        assertEquals("John Doe", address.name());
        assertEquals("1234 Chat Road,", address.lineOne());
        assertEquals("", address.lineTwo());
        assertEquals("San Francisco", address.city());
        assertEquals("CA", address.state());
        assertEquals("US", address.country());
        assertEquals("94131", address.postalCode());
    }

    @Test
    void updateRequestMatchesExampleFixture() {
        var requestJson = examples.getJsonObject("update_checkout_session_request");

        CheckoutSessionUpdateRequest request = codec.readUpdateRequest(asStream(requestJson));

        assertNull(request.items());
        assertNull(request.buyer());
        assertNull(request.fulfillmentAddress());
        assertNotNull(request.fulfillmentOptionId());
        assertEquals("fulfillment_option_456", request.fulfillmentOptionId().value());
    }

    @Test
    void completeRequestMatchesExampleFixture() {
        var requestJson = examples.getJsonObject("complete_checkout_session_request");

        CheckoutSessionCompleteRequest request = codec.readCompleteRequest(asStream(requestJson));

        assertNotNull(request.buyer());
        assertEquals("John", request.buyer().firstName());
        assertEquals("Smith", request.buyer().lastName());
        assertEquals("johnsmith@mail.com", request.buyer().email());
        assertEquals("15552003434", request.buyer().phoneNumber());
        PaymentData paymentData = request.paymentData();
        assertEquals("spt_123", paymentData.token());
        assertEquals(PaymentProvider.Provider.STRIPE, paymentData.provider());
        assertNotNull(paymentData.billingAddress());
        assertEquals("John Smith", paymentData.billingAddress().name());
    }

    private static Path examplePath(String filename) {
        return Path.of("specification", "2025-09-29", "examples", filename);
    }

    private static InputStream asStream(JsonValue value) {
        byte[] bytes = value.toString().getBytes(StandardCharsets.UTF_8);
        return new ByteArrayInputStream(bytes);
    }
}
