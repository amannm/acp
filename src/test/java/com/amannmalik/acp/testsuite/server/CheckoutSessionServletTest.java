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
import jakarta.json.JsonObject;

import org.junit.jupiter.api.Test;

import java.io.StringReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class CheckoutSessionServletTest {
    @Test
    void createAndRetrieveSession() throws Exception {
        try (var tls = TlsTestSupport.createTlsContext();
                var server = newServer(tls.configuration())) {
            server.start();
            var client = HttpClient.newBuilder().sslContext(tls.sslContext()).build();
            var baseUri = URI.create("https://localhost:" + server.httpsPort());
            var createResponse = sendCreateRequest(
                    client,
                    baseUri,
                    """
                    {"items":[{"id":"item_123","quantity":1}]}
                    """,
                    null,
                    "req-create-1");

            assertEquals(201, createResponse.statusCode());
            assertEquals("req-create-1", createResponse.headers().firstValue("Request-Id").orElseThrow());
            var sessionId = json(createResponse.body()).getString("id");

            var getResponse = client.send(
                    HttpRequest.newBuilder(baseUri.resolve("/checkout_sessions/" + sessionId))
                            .header("Authorization", "Bearer test")
                            .header("API-Version", ApiVersion.SUPPORTED)
                            .GET()
                            .build(),
                    HttpResponse.BodyHandlers.ofString());

            assertEquals(200, getResponse.statusCode());
            assertTrue(getResponse.body().contains(sessionId));
        }
    }

    @Test
    void createIdempotencyReturnsExistingSessionState() throws Exception {
        try (var tls = TlsTestSupport.createTlsContext();
                var server = newServer(tls.configuration())) {
            server.start();
            var client = HttpClient.newBuilder().sslContext(tls.sslContext()).build();
            var baseUri = URI.create("https://localhost:" + server.httpsPort());
            var body = """
                    {"items":[{"id":"item_123","quantity":1}]}
                    """;
            var first = sendCreateRequest(client, baseUri, body, "idem-create-1", "req-create-1");
            var firstSessionId = json(first.body()).getString("id");

            var second = sendCreateRequest(client, baseUri, body, "idem-create-1", "req-create-2");
            var secondSessionId = json(second.body()).getString("id");

            assertEquals(201, first.statusCode());
            assertEquals(201, second.statusCode());
            assertEquals("idem-create-1", second.headers().firstValue("Idempotency-Key").orElseThrow());
            assertEquals(firstSessionId, secondSessionId);
        }
    }

    @Test
    void createIdempotencyConflictReturns409() throws Exception {
        try (var tls = TlsTestSupport.createTlsContext();
                var server = newServer(tls.configuration())) {
            server.start();
            var client = HttpClient.newBuilder().sslContext(tls.sslContext()).build();
            var baseUri = URI.create("https://localhost:" + server.httpsPort());
            var key = "idem-create-conflict";
            var body = """
                    {"items":[{"id":"item_123","quantity":1}]}
                    """;
            var first = sendCreateRequest(client, baseUri, body, key, "req-create-3");
            assertEquals(201, first.statusCode());

            var mutated = """
                    {"items":[{"id":"item_456","quantity":1}]}
                    """;
            var second = sendCreateRequest(client, baseUri, mutated, key, "req-create-4");

            assertEquals(409, second.statusCode());
            var errorJson = Json.createReader(new StringReader(second.body())).readObject();
            assertEquals("invalid_request", errorJson.getString("type"));
            assertEquals("idempotency_conflict", errorJson.getString("code"));
        }
    }

    @Test
    void createWithUnknownItemReturns400() throws Exception {
        try (var tls = TlsTestSupport.createTlsContext();
                var server = newServer(tls.configuration())) {
            server.start();
            var client = HttpClient.newBuilder().sslContext(tls.sslContext()).build();
            var baseUri = URI.create("https://localhost:" + server.httpsPort());

            var response = sendCreateRequest(
                    client,
                    baseUri,
                    """
                    {"items":[{"id":"unknown_item","quantity":1}]}
                    """,
                    null,
                    "req-create-unknown");

            assertEquals(400, response.statusCode());
            var errorJson = json(response.body());
            assertEquals("invalid_request", errorJson.getString("type"));
            assertEquals("unknown_item", errorJson.getString("code"));
            assertEquals("$.items[0].id", errorJson.getString("param"));
        }
    }

    @Test
    void completeIdempotencyReturnsSameOrder() throws Exception {
        try (var tls = TlsTestSupport.createTlsContext();
                var server = newServer(tls.configuration())) {
            server.start();
            var client = HttpClient.newBuilder().sslContext(tls.sslContext()).build();
            var baseUri = URI.create("https://localhost:" + server.httpsPort());
            var create = sendCreateRequest(
                    client,
                    baseUri,
                    """
                    {"items":[{"id":"item_123","quantity":1}]}
                    """,
                    null,
                    "req-complete-1");
            var sessionId = json(create.body()).getString("id");

            var readyResponse = sendUpdateRequest(
                    client,
                    baseUri,
                    sessionId,
                    shippingAddressUpdate(),
                    "req-ready-1");
            assertEquals(200, readyResponse.statusCode());
            assertEquals("ready_for_payment", json(readyResponse.body()).getString("status"));

            var completeBody = """
                    {
                      "payment_data": {
                        "token": "tok_idem",
                        "provider": "stripe"
                      }
                    }
                    """;
            var first = sendCompleteRequest(client, baseUri, sessionId, completeBody, "idem-complete-1", "req-complete-2");
            assertEquals(200, first.statusCode());
            var firstJson = json(first.body());
            assertEquals("completed", firstJson.getString("status"));
            var firstOrderId = firstJson.getJsonObject("order").getString("id");

            var second = sendCompleteRequest(client, baseUri, sessionId, completeBody, "idem-complete-1", "req-complete-3");
            var secondJson = json(second.body());
            assertEquals(200, second.statusCode());
            assertEquals(firstOrderId, secondJson.getJsonObject("order").getString("id"));
            assertEquals("idem-complete-1", second.headers().firstValue("Idempotency-Key").orElseThrow());
        }
    }

    @Test
    void completeIdempotencyConflictReturns409() throws Exception {
        try (var tls = TlsTestSupport.createTlsContext();
                var server = newServer(tls.configuration())) {
            server.start();
            var client = HttpClient.newBuilder().sslContext(tls.sslContext()).build();
            var baseUri = URI.create("https://localhost:" + server.httpsPort());
            var create = sendCreateRequest(
                    client,
                    baseUri,
                    """
                    {"items":[{"id":"item_123","quantity":1}]}
                    """,
                    null,
                    "req-complete-4");
            var sessionId = json(create.body()).getString("id");

            var readyResponse = sendUpdateRequest(
                    client,
                    baseUri,
                    sessionId,
                    shippingAddressUpdate(),
                    "req-ready-2");
            assertEquals(200, readyResponse.statusCode());

            var body = """
                    {
                      "payment_data": {
                        "token": "tok_1",
                        "provider": "stripe"
                      }
                    }
                    """;
            var first = sendCompleteRequest(client, baseUri, sessionId, body, "idem-conflict", "req-complete-5");
            assertEquals(200, first.statusCode());

            var mutatedBody = """
                    {
                      "payment_data": {
                        "token": "tok_different",
                        "provider": "stripe"
                      }
                    }
                    """;
            var second = sendCompleteRequest(client, baseUri, sessionId, mutatedBody, "idem-conflict", "req-complete-6");

            assertEquals(409, second.statusCode());
            var errorJson = json(second.body());
            assertEquals("invalid_request", errorJson.getString("type"));
            assertEquals("idempotency_conflict", errorJson.getString("code"));
        }
    }

    @Test
    void completeWhenSessionNotReadyReturns409() throws Exception {
        try (var tls = TlsTestSupport.createTlsContext();
                var server = newServer(tls.configuration())) {
            server.start();
            var client = HttpClient.newBuilder().sslContext(tls.sslContext()).build();
            var baseUri = URI.create("https://localhost:" + server.httpsPort());
            var create = sendCreateRequest(
                    client,
                    baseUri,
                    """
                    {"items":[{"id":"item_123","quantity":1}]}
                    """,
                    "idem-not-ready",
                    "req-not-ready-create");
            var createdJson = json(create.body());
            assertEquals("not_ready_for_payment", createdJson.getString("status"));
            var sessionId = createdJson.getString("id");

            var completeBody = """
                    {
                      "payment_data": {
                        "token": "tok_not_ready",
                        "provider": "stripe"
                      }
                    }
                    """;
            var response = sendCompleteRequest(
                    client,
                    baseUri,
                    sessionId,
                    completeBody,
                    "idem-not-ready",
                    "req-not-ready-complete");

            assertEquals(409, response.statusCode());
            var errorJson = json(response.body());
            assertEquals("invalid_request", errorJson.getString("type"));
            assertEquals("session_not_ready", errorJson.getString("code"));
            assertEquals("$.fulfillment_address", errorJson.getString("param"));
        }
    }

    @Test
    void createIdempotencyReplayReturnsOriginalSnapshotAfterUpdate() throws Exception {
        try (var tls = TlsTestSupport.createTlsContext();
                var server = newServer(tls.configuration())) {
            server.start();
            var client = HttpClient.newBuilder().sslContext(tls.sslContext()).build();
            var baseUri = URI.create("https://localhost:" + server.httpsPort());
            var body = """
                    {"items":[{"id":"item_123","quantity":1}]}
                    """;
            var createResponse = sendCreateRequest(client, baseUri, body, "idem-snapshot", "req-initial");
            assertEquals(201, createResponse.statusCode());
            var originalJson = json(createResponse.body());
            var sessionId = originalJson.getString("id");

            var updateBody = """
                    {"items":[{"id":"item_123","quantity":2}]}
                    """;
            var updateResponse = sendUpdateRequest(client, baseUri, sessionId, updateBody, "req-update");
            assertEquals(200, updateResponse.statusCode());
            var updatedJson = json(updateResponse.body());
            assertEquals(2, updatedJson.getJsonArray("line_items").getJsonObject(0).getJsonObject("item").getInt("quantity"));

            var replay = sendCreateRequest(client, baseUri, body, "idem-snapshot", "req-replay");
            assertEquals(201, replay.statusCode());
            assertEquals("idem-snapshot", replay.headers().firstValue("Idempotency-Key").orElseThrow());
            assertEquals(originalJson, json(replay.body()));
        }
    }

    @Test
    void missingApiVersionReturns400() throws Exception {
        try (var tls = TlsTestSupport.createTlsContext();
                var server = newServer(tls.configuration())) {
            server.start();
            var client = HttpClient.newBuilder().sslContext(tls.sslContext()).build();
            var baseUri = URI.create("https://localhost:" + server.httpsPort());
            var request = HttpRequest.newBuilder(baseUri.resolve("/checkout_sessions"))
                    .header("Authorization", "Bearer test")
                    .header("Content-Type", "application/json")
                    .header("Idempotency-Key", "idem-header-missing")
                    .POST(HttpRequest.BodyPublishers.ofString("""
                            {"items":[{"id":"item_123","quantity":1}]}
                            """))
                    .build();
            var response = client.send(request, HttpResponse.BodyHandlers.ofString());
            assertEquals(400, response.statusCode());
            var errorJson = json(response.body());
            assertEquals("invalid_request", errorJson.getString("type"));
            assertEquals("missing_api_version", errorJson.getString("code"));
        }
    }

    @Test
    void completeWithoutIdempotencyKeyReturns400() throws Exception {
        try (var tls = TlsTestSupport.createTlsContext();
                var server = newServer(tls.configuration())) {
            server.start();
            var client = HttpClient.newBuilder().sslContext(tls.sslContext()).build();
            var baseUri = URI.create("https://localhost:" + server.httpsPort());
            var create = sendCreateRequest(
                    client,
                    baseUri,
                    """
                    {"items":[{"id":"item_123","quantity":1}]}
                    """,
                    null,
                    "req-complete-missing");
            var sessionId = json(create.body()).getString("id");

            var request = HttpRequest.newBuilder(baseUri.resolve("/checkout_sessions/" + sessionId + "/complete"))
                    .header("Authorization", "Bearer test")
                    .header("API-Version", ApiVersion.SUPPORTED)
                    .header("Content-Type", "application/json")
                    .header("Request-Id", "req-missing-idem")
                    .POST(HttpRequest.BodyPublishers.ofString("""
                            {"payment_data":{"token":"tok","provider":"stripe"}}
                            """))
                    .build();
            var response = client.send(request, HttpResponse.BodyHandlers.ofString());
            assertEquals(400, response.statusCode());
            var errorJson = json(response.body());
            assertEquals("invalid_request", errorJson.getString("type"));
            assertEquals("missing_idempotency_key", errorJson.getString("code"));
        }
    }

    @Test
    void updateWithUnknownFulfillmentOptionIdReturns409() throws Exception {
        try (var tls = TlsTestSupport.createTlsContext();
                var server = newServer(tls.configuration())) {
            server.start();
            var client = HttpClient.newBuilder().sslContext(tls.sslContext()).build();
            var baseUri = URI.create("https://localhost:" + server.httpsPort());
            var create = sendCreateRequest(
                    client,
                    baseUri,
                    """
                    {"items":[{"id":"item_123","quantity":1}]}
                    """,
                    null,
                    "req-update-conflict");
            var sessionId = json(create.body()).getString("id");

            var response = sendUpdateRequest(
                    client,
                    baseUri,
                    sessionId,
                    """
                    {"fulfillment_option_id":"invalid_option"}
                    """,
                    "req-update-invalid");

            assertEquals(409, response.statusCode());
            var errorJson = json(response.body());
            assertEquals("invalid_request", errorJson.getString("type"));
            assertEquals("state_conflict", errorJson.getString("code"));
        }
    }

    @Test
    void updateWithOverlyLongAddressReturns400() throws Exception {
        try (var tls = TlsTestSupport.createTlsContext();
                var server = newServer(tls.configuration())) {
            server.start();
            var client = HttpClient.newBuilder().sslContext(tls.sslContext()).build();
            var baseUri = URI.create("https://localhost:" + server.httpsPort());
            var create = sendCreateRequest(
                    client,
                    baseUri,
                    """
                    {"items":[{"id":"item_123","quantity":1}]}
                    """,
                    null,
                    "req-update-too-long");
            var sessionId = json(create.body()).getString("id");

            var longName = "N".repeat(257);
            var response = sendUpdateRequest(
                    client,
                    baseUri,
                    sessionId,
                    """
                    {
                      "fulfillment_address": {
                        "name": "%s",
                        "line_one": "123 Main St",
                        "city": "San Francisco",
                        "state": "CA",
                        "country": "US",
                        "postal_code": "94102"
                      }
                    }
                    """.formatted(longName),
                    "req-update-invalid-address");

            assertEquals(400, response.statusCode());
            var errorJson = json(response.body());
            assertEquals("invalid_request", errorJson.getString("type"));
            assertEquals("invalid_request", errorJson.getString("code"));
        }
    }

    @Test
    void cancelSessionReturnsCanceledState() throws Exception {
        try (var tls = TlsTestSupport.createTlsContext();
                var server = newServer(tls.configuration())) {
            server.start();
            var client = HttpClient.newBuilder().sslContext(tls.sslContext()).build();
            var baseUri = URI.create("https://localhost:" + server.httpsPort());
            var create = sendCreateRequest(
                    client,
                    baseUri,
                    """
                    {"items":[{"id":"item_123","quantity":1}]}
                    """,
                    null,
                    "req-cancel-create");
            var sessionId = json(create.body()).getString("id");

            var cancelResponse = client.send(
                    HttpRequest.newBuilder(baseUri.resolve("/checkout_sessions/" + sessionId + "/cancel"))
                            .header("Authorization", "Bearer test")
                            .header("API-Version", ApiVersion.SUPPORTED)
                            .header("Request-Id", "req-cancel-session")
                            .POST(HttpRequest.BodyPublishers.noBody())
                            .build(),
                    HttpResponse.BodyHandlers.ofString());

            assertEquals(200, cancelResponse.statusCode());
            var json = json(cancelResponse.body());
            assertEquals("canceled", json.getString("status"));
            assertEquals(1, json.getJsonArray("messages").size());
            assertEquals("info", json.getJsonArray("messages").getJsonObject(0).getString("type"));
        }
    }

    @Test
    void retrieveMissingSessionReturns404() throws Exception {
        try (var tls = TlsTestSupport.createTlsContext();
                var server = newServer(tls.configuration())) {
            server.start();
            var client = HttpClient.newBuilder().sslContext(tls.sslContext()).build();
            var baseUri = URI.create("https://localhost:" + server.httpsPort());

            var response = client.send(
                    HttpRequest.newBuilder(baseUri.resolve("/checkout_sessions/csn_missing"))
                            .header("Authorization", "Bearer test")
                            .header("API-Version", ApiVersion.SUPPORTED)
                            .GET()
                            .build(),
                    HttpResponse.BodyHandlers.ofString());

            assertEquals(404, response.statusCode());
            var errorJson = json(response.body());
            assertEquals("invalid_request", errorJson.getString("type"));
            assertEquals("not_found", errorJson.getString("code"));
        }
    }

    @Test
    void missingAuthorizationReturns401() throws Exception {
        try (var tls = TlsTestSupport.createTlsContext();
                var server = newServer(tls.configuration())) {
            server.start();
            var client = HttpClient.newBuilder().sslContext(tls.sslContext()).build();
            var baseUri = URI.create("https://localhost:" + server.httpsPort());

            var request = HttpRequest.newBuilder(baseUri.resolve("/checkout_sessions"))
                    .header("API-Version", ApiVersion.SUPPORTED)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString("""
                            {"items":[{"id":"item_123","quantity":1}]}
                            """))
                    .build();
            var response = client.send(request, HttpResponse.BodyHandlers.ofString());

            assertEquals(401, response.statusCode());
            var errorJson = json(response.body());
            assertEquals("invalid_request", errorJson.getString("type"));
            assertEquals("unauthorized", errorJson.getString("code"));
        }
    }

    @Test
    void createRequiresSignatureWhenSigningConfigured() throws Exception {
        var secret = Base64.getUrlDecoder().decode("c2lnbmVkX3Rlc3Qtc2VjcmV0XzEyMzQ1Njc4OTA");
        var securityConfiguration = new SecurityConfiguration(
                Set.of("test"),
                Map.of("sig", new SecurityConfiguration.SigningKey.HmacSha256(secret)),
                java.time.Duration.ofMinutes(5));
        var clock = Clock.fixed(Instant.parse("2025-11-09T12:00:00Z"), ZoneOffset.UTC);
        try (var tls = TlsTestSupport.createTlsContext();
                var server = newServer(tls.configuration(), securityConfiguration, clock)) {
            server.start();
            var client = HttpClient.newBuilder().sslContext(tls.sslContext()).build();
            var baseUri = URI.create("https://localhost:" + server.httpsPort());
            var body = "{\"items\":[{\"id\":\"item_123\",\"quantity\":1}]}";
            var timestamp = clock.instant().toString();

            var missingSignatureRequest = HttpRequest.newBuilder(baseUri.resolve("/checkout_sessions"))
                    .header("Authorization", "Bearer test")
                    .header("API-Version", ApiVersion.SUPPORTED)
                    .header("Content-Type", "application/json")
                    .header("Timestamp", timestamp)
                    .header("Request-Id", "req-missing-signature")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            var missingSignatureResponse = client.send(missingSignatureRequest, HttpResponse.BodyHandlers.ofString());
            assertEquals(400, missingSignatureResponse.statusCode());
            var missingJson = json(missingSignatureResponse.body());
            assertEquals("invalid_request", missingJson.getString("type"));
            assertEquals("missing_signature", missingJson.getString("code"));

            var signature = SigningTestSupport.hmacSignature(secret, timestamp, body);
            var signedRequest = HttpRequest.newBuilder(baseUri.resolve("/checkout_sessions"))
                    .header("Authorization", "Bearer test")
                    .header("API-Version", ApiVersion.SUPPORTED)
                    .header("Content-Type", "application/json")
                    .header("Timestamp", timestamp)
                    .header("Signature", "sig:" + signature)
                    .header("Request-Id", "req-signed-create")
                    .header("Idempotency-Key", "idem-signed-create")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            var signedResponse = client.send(signedRequest, HttpResponse.BodyHandlers.ofString());
            assertEquals(201, signedResponse.statusCode());
            var sessionJson = json(signedResponse.body());
            assertTrue(sessionJson.containsKey("id"));
        }
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
                java.time.Duration.ofMinutes(5));
    }

    private static HttpResponse<String> sendCreateRequest(
            HttpClient client,
            URI baseUri,
            String body,
            String idempotencyKey,
            String requestId) throws Exception {
        var builder = HttpRequest.newBuilder(baseUri.resolve("/checkout_sessions"))
                .header("Authorization", "Bearer test")
                .header("API-Version", ApiVersion.SUPPORTED)
                .header("Content-Type", "application/json")
                .header("Request-Id", requestId)
                .POST(HttpRequest.BodyPublishers.ofString(body));
        if (idempotencyKey != null) {
            builder.header("Idempotency-Key", idempotencyKey);
        }
        return client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private static HttpResponse<String> sendUpdateRequest(
            HttpClient client,
            URI baseUri,
            String sessionId,
            String body,
            String requestId) throws Exception {
        return client.send(
                HttpRequest.newBuilder(baseUri.resolve("/checkout_sessions/" + sessionId))
                        .header("Authorization", "Bearer test")
                        .header("API-Version", ApiVersion.SUPPORTED)
                        .header("Content-Type", "application/json")
                        .header("Request-Id", requestId)
                        .POST(HttpRequest.BodyPublishers.ofString(body))
                        .build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private static HttpResponse<String> sendCompleteRequest(
            HttpClient client,
            URI baseUri,
            String sessionId,
            String body,
            String idempotencyKey,
            String requestId) throws Exception {
        var builder = HttpRequest.newBuilder(baseUri.resolve("/checkout_sessions/" + sessionId + "/complete"))
                .header("Authorization", "Bearer test")
                .header("API-Version", ApiVersion.SUPPORTED)
                .header("Content-Type", "application/json")
                .header("Idempotency-Key", idempotencyKey)
                .header("Request-Id", requestId)
                .POST(HttpRequest.BodyPublishers.ofString(body));
        return client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private static String shippingAddressUpdate() {
        return """
                {
                  "fulfillment_address": {
                    "name": "John Doe",
                    "line_one": "1234 Chat Road",
                    "line_two": "",
                    "city": "San Francisco",
                    "state": "CA",
                    "country": "US",
                    "postal_code": "94131"
                  }
                }
                """;
    }

    private static JsonObject json(String body) {
        return Json.createReader(new StringReader(body)).readObject();
    }
}
