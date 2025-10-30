package com.amannmalik.acp.testsuite.server;

import com.amannmalik.acp.api.checkout.InMemoryCheckoutSessionService;
import com.amannmalik.acp.api.delegatepayment.InMemoryDelegatePaymentService;
import com.amannmalik.acp.api.shared.ApiVersion;
import com.amannmalik.acp.server.JettyHttpServer;
import com.amannmalik.acp.server.TlsConfiguration;
import com.amannmalik.acp.server.security.ConfigurableRequestAuthenticator;
import com.amannmalik.acp.server.security.SecurityConfiguration;
import com.amannmalik.acp.testutil.TlsTestSupport;

import jakarta.json.Json;

import org.junit.jupiter.api.Test;

import java.io.StringReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Clock;
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
            var sessionId = Json.createReader(new StringReader(createResponse.body())).readObject().getString("id");

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
            var firstSessionId = Json.createReader(new StringReader(first.body())).readObject().getString("id");

            var second = sendCreateRequest(client, baseUri, body, "idem-create-1", "req-create-2");
            var secondSessionId = Json.createReader(new StringReader(second.body())).readObject().getString("id");

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
            assertEquals("request_not_idempotent", errorJson.getString("type"));
            assertEquals("idempotency_conflict", errorJson.getString("code"));
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
            var sessionId = Json.createReader(new StringReader(create.body())).readObject().getString("id");

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
            var firstJson = Json.createReader(new StringReader(first.body())).readObject();
            assertEquals("completed", firstJson.getString("status"));
            var firstOrderId = firstJson.getJsonObject("order").getString("id");

            var second = sendCompleteRequest(client, baseUri, sessionId, completeBody, "idem-complete-1", "req-complete-3");
            var secondJson = Json.createReader(new StringReader(second.body())).readObject();
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
            var sessionId = Json.createReader(new StringReader(create.body())).readObject().getString("id");

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
            var errorJson = Json.createReader(new StringReader(second.body())).readObject();
            assertEquals("request_not_idempotent", errorJson.getString("type"));
            assertEquals("idempotency_conflict", errorJson.getString("code"));
        }
    }

    private static JettyHttpServer newServer(TlsConfiguration tlsConfiguration) {
        var checkout = new InMemoryCheckoutSessionService();
        var delegate = new InMemoryDelegatePaymentService();
        var authenticator = new ConfigurableRequestAuthenticator(
                new SecurityConfiguration(Set.of("test"), Map.of(), java.time.Duration.ofMinutes(5)),
                Clock.systemUTC());
        return new JettyHttpServer(JettyHttpServer.Configuration.httpsOnly(tlsConfiguration), checkout, delegate, authenticator);
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
}
