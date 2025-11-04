package com.amannmalik.acp.testsuite.codec;

import com.amannmalik.acp.api.checkout.model.Address;
import com.amannmalik.acp.api.delegatepayment.model.Allowance;
import com.amannmalik.acp.api.delegatepayment.model.DelegatePaymentRequest;
import com.amannmalik.acp.api.delegatepayment.model.PaymentMethodCard;
import com.amannmalik.acp.api.delegatepayment.model.RiskSignal;
import com.amannmalik.acp.api.shared.CurrencyCode;
import com.amannmalik.acp.codec.DelegatePaymentJsonCodec;
import jakarta.json.Json;
import jakarta.json.JsonObject;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

final class DelegatePaymentJsonCodecExamplesTest {
    private static JsonObject examples;
    private final DelegatePaymentJsonCodec codec = new DelegatePaymentJsonCodec();

    @BeforeAll
    static void loadExamples() throws Exception {
        try (var reader = Json.createReader(Files.newInputStream(examplePath("examples.delegate_payment.json")))) {
            examples = reader.readObject();
        }
    }

    @Test
    void requestMatchesExampleFixture() {
        var requestJson = examples.getJsonObject("delegate_payment_request");

        DelegatePaymentRequest request = codec.readRequest(asStream(requestJson));

        PaymentMethodCard card = request.paymentMethod();
        assertEquals(PaymentMethodCard.CardNumberType.FPAN, card.cardNumberType());
        assertEquals("4242424242424242", card.number());
        assertEquals("11", card.expMonth());
        assertEquals("2026", card.expYear());
        assertEquals("Jane Doe", card.name());
        assertEquals("223", card.cvc());
        assertEquals(List.of(
                PaymentMethodCard.Check.AVS,
                PaymentMethodCard.Check.CVV), card.checksPerformed());
        assertEquals(PaymentMethodCard.DisplayCardFundingType.CREDIT, card.displayCardFundingType());
        Allowance allowance = request.allowance();
        assertEquals(Allowance.Reason.ONE_TIME, allowance.reason());
        assertEquals(2000L, allowance.maxAmount().value());
        assertEquals(new CurrencyCode("usd"), allowance.currency());
        assertEquals("csn_01HV3P3XYZ9ABC", allowance.checkoutSessionId());
        assertEquals("acme_store", allowance.merchantId());
        assertEquals(Instant.parse("2025-10-09T07:20:50.52Z"), allowance.expiresAt());
        Address address = request.billingAddress();
        assertNotNull(address);
        assertEquals("Ada Lovelace", address.name());
        assertEquals("1234 Chat Road", address.lineOne());
        assertEquals("", address.lineTwo());
        assertEquals("San Francisco", address.city());
        assertEquals("CA", address.state());
        assertEquals("US", address.country());
        assertEquals("94131", address.postalCode());
        List<RiskSignal> riskSignals = request.riskSignals();
        assertEquals(1, riskSignals.size());
        assertEquals(RiskSignal.Type.CARD_TESTING, riskSignals.get(0).type());
        assertEquals(10, riskSignals.get(0).score());
        assertEquals(RiskSignal.Action.MANUAL_REVIEW, riskSignals.get(0).action());
        Map<String, String> metadata = request.metadata();
        assertEquals("q4", metadata.get("campaign"));
        assertEquals("chatgpt_checkout", metadata.get("source"));
    }

    private static Path examplePath(String filename) {
        return Path.of("specification", "2025-09-29", "examples", filename);
    }

    private static InputStream asStream(JsonObject value) {
        byte[] bytes = value.toString().getBytes(StandardCharsets.UTF_8);
        return new ByteArrayInputStream(bytes);
    }
}
