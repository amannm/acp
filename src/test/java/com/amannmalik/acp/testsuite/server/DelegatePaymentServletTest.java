package com.amannmalik.acp.testsuite.server;

import com.amannmalik.acp.api.checkout.InMemoryCheckoutSessionService;
import com.amannmalik.acp.api.delegatepayment.InMemoryDelegatePaymentService;
import com.amannmalik.acp.api.shared.ApiVersion;
import com.amannmalik.acp.server.JettyHttpServer;

import jakarta.json.Json;

import org.junit.jupiter.api.Test;

import java.io.StringReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class DelegatePaymentServletTest {
    private static final String VALID_REQUEST_BODY = """
            {
              "payment_method": {
                "type": "card",
                "card_number_type": "fpan",
                "virtual": false,
                "number": "4242424242424242",
                "exp_month": "11",
                "exp_year": "2026",
                "display_card_funding_type": "credit",
                "metadata": {"issuer": "demo"}
              },
              "allowance": {
                "reason": "one_time",
                "max_amount": 2000,
                "currency": "usd",
                "checkout_session_id": "csn_test",
                "merchant_id": "acme",
                "expires_at": "2030-01-01T00:00:00Z"
              },
              "billing_address": {
                "name": "Jane Doe",
                "line_one": "123 Main St",
                "city": "San Francisco",
                "state": "CA",
                "country": "US",
                "postal_code": "94102"
              },
              "risk_signals": [
                {"type": "card_testing", "score": 1, "action": "authorized"}
              ],
              "metadata": {"source": "test"}
            }
            """;

    @Test
    void createDelegatePaymentToken() throws Exception {
        try (var server = new JettyHttpServer(
                0,
                new InMemoryCheckoutSessionService(),
                new InMemoryDelegatePaymentService())) {
            server.start();
            var client = HttpClient.newHttpClient();
            var response = sendDelegatePaymentRequest(client, serverBaseUri(server), "idem_create", VALID_REQUEST_BODY);

            assertEquals(201, response.statusCode());
            var json = Json.createReader(new StringReader(response.body())).readObject();
            assertTrue(json.containsKey("id"));
            assertTrue(json.getJsonObject("metadata").containsKey("merchant_id"));
        }
    }

    @Test
    void idempotencyConflictReturns409() throws Exception {
        try (var server = new JettyHttpServer(
                0,
                new InMemoryCheckoutSessionService(),
                new InMemoryDelegatePaymentService())) {
            server.start();
            var client = HttpClient.newHttpClient();
            var baseBody = VALID_REQUEST_BODY;
            var idemKey = "idem_conflict";
            var first = sendDelegatePaymentRequest(client, serverBaseUri(server), idemKey, baseBody);
            assertEquals(201, first.statusCode());

            var mutatedBody = baseBody.replace("\"max_amount\": 2000", "\"max_amount\": 3000");
            var second = sendDelegatePaymentRequest(client, serverBaseUri(server), idemKey, mutatedBody);

            assertEquals(409, second.statusCode());
            assertTrue(second.body().contains("idempotency_conflict"));
        }
    }

    @Test
    void expiredAllowanceReturns400() throws Exception {
        try (var server = new JettyHttpServer(
                0,
                new InMemoryCheckoutSessionService(),
                new InMemoryDelegatePaymentService())) {
            server.start();
            var client = HttpClient.newHttpClient();
            var expiredBody = VALID_REQUEST_BODY.replace("2030-01-01T00:00:00Z", "2020-01-01T00:00:00Z");
            var response = sendDelegatePaymentRequest(client, serverBaseUri(server), "idem_expired", expiredBody);

            assertEquals(400, response.statusCode());
            assertTrue(response.body().contains("expires_at"));
        }
    }

    private static URI serverBaseUri(JettyHttpServer server) {
        return URI.create("http://localhost:" + server.port());
    }

    private static HttpResponse<String> sendDelegatePaymentRequest(HttpClient client, URI baseUri, String idemKey, String body)
            throws Exception {
        var request = HttpRequest.newBuilder(baseUri.resolve("/agentic_commerce/delegate_payment"))
                .header("Authorization", "Bearer test")
                .header("API-Version", ApiVersion.SUPPORTED)
                .header("Content-Type", "application/json")
                .header("Idempotency-Key", idemKey)
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }
}
