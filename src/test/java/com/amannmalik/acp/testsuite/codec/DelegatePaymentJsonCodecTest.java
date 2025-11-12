package com.amannmalik.acp.testsuite.codec;

import com.amannmalik.acp.api.delegatepayment.model.DelegatePaymentRequest;
import com.amannmalik.acp.codec.DelegatePaymentJsonCodec;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

final class DelegatePaymentJsonCodecTest {
    @Test
    void readRequestRetainsNetworkTokenArtifacts() {
        var json = """
                {
                  "payment_method": {
                    "type": "card",
                    "card_number_type": "network_token",
                    "virtual": true,
                    "number": "4288880000000000",
                    "cryptogram": "Zm9vYmFy",
                    "eci_value": "05",
                    "display_card_funding_type": "credit",
                    "metadata": {}
                  },
                  "allowance": {
                    "reason": "one_time",
                    "max_amount": 1000,
                    "currency": "usd",
                    "checkout_session_id": "csn_test",
                    "merchant_id": "acme",
                    "expires_at": "2030-01-01T00:00:00Z"
                  },
                  "risk_signals": [
                    {"type": "card_testing", "score": 1, "action": "authorized"}
                  ],
                  "metadata": {}
                }
                """;
        var codec = new DelegatePaymentJsonCodec();
        DelegatePaymentRequest request = codec.readRequest(new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)));

        var paymentMethod = request.paymentMethod();
        assertNotNull(paymentMethod);
        assertEquals("Zm9vYmFy", paymentMethod.cryptogram());
        assertEquals("05", paymentMethod.eciValue());
    }
}
