package com.amannmalik.acp.testsuite.server;

import com.amannmalik.acp.api.checkout.InMemoryCheckoutSessionService;
import com.amannmalik.acp.api.delegatepayment.InMemoryDelegatePaymentService;
import com.amannmalik.acp.api.shared.ApiVersion;
import com.amannmalik.acp.server.JettyHttpServer;
import com.amannmalik.acp.server.TlsConfiguration;
import com.amannmalik.acp.server.security.ConfigurableRequestAuthenticator;
import com.amannmalik.acp.server.security.SecurityConfiguration;
import com.amannmalik.acp.testutil.SigningTestSupport;
import com.amannmalik.acp.testutil.TlsTestSupport;
import jakarta.json.Json;
import org.junit.jupiter.api.Test;

import java.io.StringReader;
import java.net.URI;
import java.net.http.*;
import java.time.*;
import java.util.*;

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
                "cryptogram": "AAECAw==",
                "eci_value": "05",
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

    private static final String SIGNABLE_REQUEST_BODY =
            "{\"allowance\":{\"checkout_session_id\":\"csn_sig\",\"currency\":\"usd\",\"expires_at\":\"2030-01-01T00:00:00Z\",\"max_amount\":2000,\"merchant_id\":\"acme\",\"reason\":\"one_time\"},\"metadata\":{\"source\":\"test\"},\"payment_method\":{\"card_number_type\":\"fpan\",\"display_card_funding_type\":\"credit\",\"metadata\":{\"issuer\":\"demo\"},\"number\":\"4242424242424242\",\"type\":\"card\",\"virtual\":false},\"risk_signals\":[{\"action\":\"authorized\",\"score\":1,\"type\":\"card_testing\"}]}";

    private static URI serverBaseUri(JettyHttpServer server) {
        return URI.create("https://localhost:" + server.httpsPort());
    }

    private static HttpResponse<String> sendDelegatePaymentRequest(HttpClient client, URI baseUri, String idemKey, String body)
            throws Exception {
        var request = HttpRequest.newBuilder(baseUri.resolve("/agentic_commerce/delegate_payment"))
                .header("Authorization", "Bearer test")
                .header("API-Version", ApiVersion.SUPPORTED)
                .header("Content-Type", "application/json")
                .header("Idempotency-Key", idemKey)
                .header("Request-Id", "req-" + idemKey)
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private static JettyHttpServer newServer(TlsConfiguration tlsConfiguration) {
        return newServer(tlsConfiguration, defaultSecurityConfiguration(), Clock.systemUTC());
    }

    private static JettyHttpServer newServer(
            TlsConfiguration tlsConfiguration,
            SecurityConfiguration securityConfiguration,
            Clock clock) {
        var checkout = new InMemoryCheckoutSessionService();
        var delegate = new InMemoryDelegatePaymentService();
        var authenticator = new ConfigurableRequestAuthenticator(securityConfiguration, clock);
        return new JettyHttpServer(JettyHttpServer.Configuration.httpsOnly(tlsConfiguration), checkout, delegate, authenticator);
    }

    private static SecurityConfiguration defaultSecurityConfiguration() {
        return new SecurityConfiguration(
                Set.of("test"),
                Map.of(),
                Duration.ofMinutes(5));
    }

    @Test
    void createDelegatePaymentToken() throws Exception {
        try (var tls = TlsTestSupport.createTlsContext();
             var server = newServer(tls.configuration())) {
            server.start();
            var client = HttpClient.newBuilder().sslContext(tls.sslContext()).build();
            var response = sendDelegatePaymentRequest(client, serverBaseUri(server), "idem_create", VALID_REQUEST_BODY);

            assertEquals(201, response.statusCode());
            assertEquals("req-idem_create", response.headers().firstValue("Request-Id").orElseThrow());
            assertEquals("idem_create", response.headers().firstValue("Idempotency-Key").orElseThrow());
            var json = Json.createReader(new StringReader(response.body())).readObject();
            assertTrue(json.containsKey("id"));
            assertTrue(json.getJsonObject("metadata").containsKey("merchant_id"));
        }
    }

    @Test
    void idempotencyConflictReturns409() throws Exception {
        try (var tls = TlsTestSupport.createTlsContext();
             var server = newServer(tls.configuration())) {
            server.start();
            var client = HttpClient.newBuilder().sslContext(tls.sslContext()).build();
            var baseBody = VALID_REQUEST_BODY;
            var idemKey = "idem_conflict";
            var first = sendDelegatePaymentRequest(client, serverBaseUri(server), idemKey, baseBody);
            assertEquals(201, first.statusCode());

            var mutatedBody = baseBody.replace("\"max_amount\": 2000", "\"max_amount\": 3000");
            var second = sendDelegatePaymentRequest(client, serverBaseUri(server), idemKey, mutatedBody);

            assertEquals(409, second.statusCode());
            var errorJson = Json.createReader(new StringReader(second.body())).readObject();
            assertEquals("invalid_request", errorJson.getString("type"));
            assertEquals("idempotency_conflict", errorJson.getString("code"));
        }
    }

    @Test
    void expiredAllowanceReturns422() throws Exception {
        try (var tls = TlsTestSupport.createTlsContext();
             var server = newServer(tls.configuration())) {
            server.start();
            var client = HttpClient.newBuilder().sslContext(tls.sslContext()).build();
            var expiredBody = VALID_REQUEST_BODY.replace("2030-01-01T00:00:00Z", "2020-01-01T00:00:00Z");
            var response = sendDelegatePaymentRequest(client, serverBaseUri(server), "idem_expired", expiredBody);

            assertEquals(422, response.statusCode());
            var errorJson = Json.createReader(new StringReader(response.body())).readObject();
            assertEquals("invalid_request", errorJson.getString("type"));
            assertEquals("invalid_card", errorJson.getString("code"));
            assertEquals("$.allowance.expires_at", errorJson.getString("param"));
        }
    }

    @Test
    void zeroMaxAmountReturns422() throws Exception {
        try (var tls = TlsTestSupport.createTlsContext();
             var server = newServer(tls.configuration())) {
            server.start();
            var client = HttpClient.newBuilder().sslContext(tls.sslContext()).build();
            var body = VALID_REQUEST_BODY.replace("\"max_amount\": 2000", "\"max_amount\": 0");

            var response = sendDelegatePaymentRequest(client, serverBaseUri(server), "idem_zero_amount", body);

            assertEquals(422, response.statusCode());
            var errorJson = Json.createReader(new StringReader(response.body())).readObject();
            assertEquals("invalid_request", errorJson.getString("type"));
            assertEquals("invalid_card", errorJson.getString("code"));
            assertEquals("$.allowance.max_amount", errorJson.getString("param"));
        }
    }

    @Test
    void missingAuthorizationReturns401() throws Exception {
        try (var tls = TlsTestSupport.createTlsContext();
             var server = newServer(tls.configuration())) {
            server.start();
            var client = HttpClient.newBuilder().sslContext(tls.sslContext()).build();
            var request = HttpRequest.newBuilder(serverBaseUri(server).resolve("/agentic_commerce/delegate_payment"))
                    .header("API-Version", ApiVersion.SUPPORTED)
                    .header("Content-Type", "application/json")
                    .header("Request-Id", "req-missing-auth")
                    .POST(HttpRequest.BodyPublishers.ofString(VALID_REQUEST_BODY))
                    .build();

            var response = client.send(request, HttpResponse.BodyHandlers.ofString());

            assertEquals(401, response.statusCode());
            var errorJson = Json.createReader(new StringReader(response.body())).readObject();
            assertEquals("invalid_request", errorJson.getString("type"));
            assertEquals("unauthorized", errorJson.getString("code"));
        }
    }

    @Test
    void merchantIdLongerThan256Returns400() throws Exception {
        try (var tls = TlsTestSupport.createTlsContext();
             var server = newServer(tls.configuration())) {
            server.start();
            var client = HttpClient.newBuilder().sslContext(tls.sslContext()).build();
            var longMerchant = "m".repeat(257);
            var body = VALID_REQUEST_BODY.replace("\"merchant_id\": \"acme\"", "\"merchant_id\": \"" + longMerchant + "\"");

            var response = sendDelegatePaymentRequest(
                    client,
                    serverBaseUri(server),
                    "idem-long-merchant",
                    body);

            assertEquals(400, response.statusCode());
            var errorJson = Json.createReader(new StringReader(response.body())).readObject();
            assertEquals("invalid_request", errorJson.getString("type"));
            assertEquals("invalid_card", errorJson.getString("code"));
        }
    }

    @Test
    void delegatePaymentRequiresSignatureWhenConfigured() throws Exception {
        var secret = Base64.getUrlDecoder().decode("c2lnbmVkX3Rlc3Qtc2VjcmV0XzEyMzQ1Njc4OTA");
        var securityConfiguration = new SecurityConfiguration(
                Set.of("test"),
                Map.of("sig", new SecurityConfiguration.SigningKey.HmacSha256(secret)),
                Duration.ofMinutes(5));
        var clock = Clock.fixed(Instant.parse("2025-11-09T12:00:00Z"), ZoneOffset.UTC);
        try (var tls = TlsTestSupport.createTlsContext();
             var server = newServer(tls.configuration(), securityConfiguration, clock)) {
            server.start();
            var client = HttpClient.newBuilder().sslContext(tls.sslContext()).build();
            var baseUri = serverBaseUri(server);
            var timestamp = clock.instant().toString();

            var missingSignatureRequest = HttpRequest.newBuilder(baseUri.resolve("/agentic_commerce/delegate_payment"))
                    .header("Authorization", "Bearer test")
                    .header("API-Version", ApiVersion.SUPPORTED)
                    .header("Content-Type", "application/json")
                    .header("Idempotency-Key", "idem-sig-missing")
                    .header("Request-Id", "req-sig-missing")
                    .header("Timestamp", timestamp)
                    .POST(HttpRequest.BodyPublishers.ofString(SIGNABLE_REQUEST_BODY))
                    .build();
            var missingSignatureResponse = client.send(missingSignatureRequest, HttpResponse.BodyHandlers.ofString());
            assertEquals(400, missingSignatureResponse.statusCode());
            var missingJson = Json.createReader(new StringReader(missingSignatureResponse.body())).readObject();
            assertEquals("invalid_request", missingJson.getString("type"));
            assertEquals("missing_signature", missingJson.getString("code"));

            var signature = SigningTestSupport.hmacSignature(secret, timestamp, SIGNABLE_REQUEST_BODY);
            var signedRequest = HttpRequest.newBuilder(baseUri.resolve("/agentic_commerce/delegate_payment"))
                    .header("Authorization", "Bearer test")
                    .header("API-Version", ApiVersion.SUPPORTED)
                    .header("Content-Type", "application/json")
                    .header("Idempotency-Key", "idem-signed-delegate")
                    .header("Request-Id", "req-signed-delegate")
                    .header("Timestamp", timestamp)
                    .header("Signature", "sig:" + signature)
                    .POST(HttpRequest.BodyPublishers.ofString(SIGNABLE_REQUEST_BODY))
                    .build();
            var signedResponse = client.send(signedRequest, HttpResponse.BodyHandlers.ofString());
            assertEquals(201, signedResponse.statusCode());
            var responseJson = Json.createReader(new StringReader(signedResponse.body())).readObject();
            assertTrue(responseJson.containsKey("id"));
        }
    }
}
