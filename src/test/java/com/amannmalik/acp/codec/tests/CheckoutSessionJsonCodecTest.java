package com.amannmalik.acp.codec.tests;

import com.amannmalik.acp.codec.CheckoutSessionJsonCodec;
import com.amannmalik.acp.codec.JsonDecodingException;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

final class CheckoutSessionJsonCodecTest {
    @Test
    void createRequestRejectsAddressesWithoutState() {
        var payload = """
                {
                  "items":[{"id":"item_123","quantity":1}],
                  "fulfillment_address":{
                    "name":"Jane Doe",
                    "line_one":"1234 Chat Road",
                    "city":"San Francisco",
                    "country":"us",
                    "postal_code":"94131"
                  }
                }
                """;
        var codec = new CheckoutSessionJsonCodec();
        assertThrows(
                JsonDecodingException.class,
                () -> codec.readCreateRequest(new ByteArrayInputStream(payload.getBytes(StandardCharsets.UTF_8))));
    }
}
