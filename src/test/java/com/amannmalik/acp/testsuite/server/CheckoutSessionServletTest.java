package com.amannmalik.acp.testsuite.server;

import com.amannmalik.acp.api.checkout.InMemoryCheckoutSessionService;
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

final class CheckoutSessionServletTest {
    @Test
    void createAndRetrieveSession() throws Exception {
        try (var server = new JettyHttpServer(0, new InMemoryCheckoutSessionService())) {
            server.start();
            var client = HttpClient.newHttpClient();
            var baseUri = URI.create("http://localhost:" + server.port());
            var createResponse = client.send(
                    HttpRequest.newBuilder(baseUri.resolve("/checkout_sessions"))
                            .header("Authorization", "Bearer test")
                            .header("API-Version", ApiVersion.SUPPORTED)
                            .header("Content-Type", "application/json")
                            .POST(HttpRequest.BodyPublishers.ofString("{" +
                                    "\"items\":[{\"id\":\"item_123\",\"quantity\":1}]" +
                                    "}"))
                            .build(),
                    HttpResponse.BodyHandlers.ofString());

            assertEquals(201, createResponse.statusCode());
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
}
