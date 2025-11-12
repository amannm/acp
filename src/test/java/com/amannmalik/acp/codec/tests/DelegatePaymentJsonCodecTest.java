package com.amannmalik.acp.codec.tests;

import com.amannmalik.acp.codec.DelegatePaymentJsonCodec;
import com.amannmalik.acp.api.delegatepayment.model.DelegatePaymentRequest;
import org.junit.jupiter.api.Test;

import jakarta.json.Json;
import jakarta.json.JsonObject;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

final class DelegatePaymentJsonCodecTest {
    @Test
    void readRequest_allowsBlankBillingAddressLineTwo() throws Exception {
        var examples = readExamples("specification/2025-09-29/examples/examples.delegate_payment.json");
        var requestJson = examples.getJsonObject("delegate_payment_request");
        var codec = new DelegatePaymentJsonCodec();
        DelegatePaymentRequest request = codec.readRequest(jsonBytes(requestJson));
        assertNotNull(request.billingAddress());
        assertEquals("", request.billingAddress().lineTwo());
    }

    @Test
    void readRequest_rejectsInvalidExpMonth() throws Exception {
        var examples = readExamples("specification/2025-09-29/examples/examples.delegate_payment.json");
        var requestJson = examples.getJsonObject("delegate_payment_request");
        var invalidPaymentMethod = Json.createObjectBuilder(requestJson.getJsonObject("payment_method"))
                .add("exp_month", "13")
                .build();
        var invalid = Json.createObjectBuilder(requestJson)
                .add("payment_method", invalidPaymentMethod)
                .build();
        var codec = new DelegatePaymentJsonCodec();
        assertThrows(IllegalArgumentException.class, () -> codec.readRequest(jsonBytes(invalid)));
    }

    @Test
    void readRequest_defaultsVirtualToFalseWhenMissing() throws Exception {
        var examples = readExamples("specification/2025-09-29/examples/examples.delegate_payment.json");
        var requestJson = examples.getJsonObject("delegate_payment_request");
        var paymentMethodWithoutVirtual = Json.createObjectBuilder();
        requestJson.getJsonObject("payment_method").forEach((key, value) -> {
            if (!"virtual".equals(key)) {
                paymentMethodWithoutVirtual.add(key, value);
            }
        });
        var mutated = Json.createObjectBuilder(requestJson)
                .add("payment_method", paymentMethodWithoutVirtual.build())
                .build();
        var codec = new DelegatePaymentJsonCodec();

        var request = codec.readRequest(jsonBytes(mutated));

        assertFalse(request.paymentMethod().virtual());
    }

    @Test
    void readRequest_allowsMissingBillingAddressState() throws Exception {
        var examples = readExamples("specification/2025-09-29/examples/examples.delegate_payment.json");
        var requestJson = examples.getJsonObject("delegate_payment_request");
        var billingWithoutState = Json.createObjectBuilder();
        requestJson.getJsonObject("billing_address").forEach((key, value) -> {
            if (!"state".equals(key)) {
                billingWithoutState.add(key, value);
            }
        });
        var requestWithoutState = Json.createObjectBuilder(requestJson)
                .add("billing_address", billingWithoutState.build())
                .build();
        var codec = new DelegatePaymentJsonCodec();
        var request = codec.readRequest(jsonBytes(requestWithoutState));
        assertNotNull(request.billingAddress());
        assertNull(request.billingAddress().state());
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
