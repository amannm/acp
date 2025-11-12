package com.amannmalik.acp.testsuite.codec;

import com.amannmalik.acp.api.checkout.model.*;
import com.amannmalik.acp.codec.CheckoutSessionJsonCodec;
import jakarta.json.*;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

final class CheckoutSessionJsonCodecExamplesTest {
    private static JsonObject examples;
    private final CheckoutSessionJsonCodec codec = new CheckoutSessionJsonCodec();

    @BeforeAll
    static void loadExamples() throws Exception {
        try (var reader = Json.createReader(Files.newInputStream(examplePath("examples.agentic_checkout.json")))) {
            examples = reader.readObject();
        }
    }

    private static Path examplePath(String filename) {
        return Path.of("specification", "2025-09-29", "examples", filename);
    }

    private static InputStream asStream(JsonValue value) {
        byte[] bytes = value.toString().getBytes(StandardCharsets.UTF_8);
        return new ByteArrayInputStream(bytes);
    }

    @Test
    void createRequestMatchesExampleFixture() {
        var requestJson = examples.getJsonObject("create_checkout_session_request");

        CheckoutSessionCreateRequest request = codec.readCreateRequest(asStream(requestJson));

        List<Item> items = request.items();
        assertEquals(1, items.size());
        assertEquals("item_123", items.getFirst().id());
        assertEquals(1, items.getFirst().quantity());
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

    @Test
    void createResponseRoundTripsSpecExample() throws Exception {
        assertRoundTrip("create_checkout_session_response");
    }

    @Test
    void updateResponseRoundTripsSpecExample() throws Exception {
        assertRoundTrip("update_checkout_session_response");
    }

    @Test
    void completeResponseRoundTripsSpecExample() throws Exception {
        assertRoundTrip("complete_checkout_session_response");
    }

    @Test
    void cancelResponseRoundTripsSpecExample() throws Exception {
        assertRoundTrip("cancel_checkout_session_response");
    }

    private void assertRoundTrip(String exampleKey) {
        var expected = examples.getJsonObject(exampleKey);
        var session = codec.readCheckoutSession(asStream(expected));
        var output = new ByteArrayOutputStream();
        codec.writeCheckoutSession(output, session);
        var actual = Json.createReader(new ByteArrayInputStream(output.toByteArray())).readObject();
        assertJsonSubset(expected, actual, exampleKey);
    }

    private void assertJsonSubset(JsonValue expected, JsonValue actual, String context) {
        var expectedType = expected.getValueType();
        assertEquals(expectedType, actual.getValueType(), context + ": value type mismatch");
        switch (expectedType) {
            case OBJECT -> {
                var expectedObject = expected.asJsonObject();
                var actualObject = actual.asJsonObject();
                for (var entry : expectedObject.entrySet()) {
                    var key = entry.getKey();
                    assertTrue(actualObject.containsKey(key), context + ": missing key " + key);
                    assertJsonSubset(entry.getValue(), actualObject.get(key), context + "." + key);
                }
            }
            case ARRAY -> {
                var expectedArray = expected.asJsonArray();
                var actualArray = actual.asJsonArray();
                assertEquals(expectedArray.size(), actualArray.size(), context + ": array size differs");
                for (int i = 0; i < expectedArray.size(); i++) {
                    assertJsonSubset(expectedArray.get(i), actualArray.get(i), context + "[" + i + "]");
                }
            }
            case STRING -> {
                var expectedString = ((JsonString) expected).getString();
                var actualString = ((JsonString) actual).getString();
                if (looksLikeInstant(expectedString) && looksLikeInstant(actualString)) {
                    assertEquals(
                            Instant.parse(expectedString),
                            Instant.parse(actualString),
                            context + ": instant mismatch");
                } else {
                    assertEquals(expectedString, actualString, context + ": string mismatch");
                }
            }
            default -> assertEquals(expected, actual, context + ": scalar mismatch");
        }
    }

    private boolean looksLikeInstant(String candidate) {
        try {
            Instant.parse(candidate);
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }
}
