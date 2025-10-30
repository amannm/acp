package com.amannmalik.acp.server.webhook;

import com.amannmalik.acp.util.Ensure;

import jakarta.json.Json;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

public final class HttpOrderWebhookPublisher implements OrderWebhookPublisher {
    private static final Base64.Encoder SIGNATURE_ENCODER = Base64.getUrlEncoder().withoutPadding();

    private final HttpClient httpClient;
    private final URI endpoint;
    private final String signatureHeader;
    private final byte[] secret;
    private final Clock clock;

    public HttpOrderWebhookPublisher(HttpClient httpClient, URI endpoint, String signatureHeader, byte[] secret, Clock clock) {
        this.httpClient = Ensure.notNull("webhook.http_client", httpClient);
        this.endpoint = Ensure.notNull("webhook.endpoint", endpoint);
        this.signatureHeader = Ensure.nonBlank("webhook.signature_header", signatureHeader);
        this.secret = secret.clone();
        this.clock = clock == null ? Clock.systemUTC() : clock;
    }

    @Override
    public void publish(OrderWebhookEvent event) {
        var payload = serialize(event);
        var timestamp = clock.instant();
        var signature = sign(payload, timestamp);
        var request = HttpRequest.newBuilder(endpoint)
                .header("Content-Type", "application/json")
                .header(signatureHeader, signature)
                .header("Timestamp", timestamp.toString())
                .POST(HttpRequest.BodyPublishers.ofString(payload))
                .build();
        try {
            httpClient.send(request, HttpResponse.BodyHandlers.discarding());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Webhook dispatch interrupted", e);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to publish order webhook", e);
        }
    }

    private String serialize(OrderWebhookEvent event) {
        var dataBuilder = Json.createObjectBuilder()
                .add("type", "order")
                .add("checkout_session_id", event.checkoutSessionId())
                .add("permalink_url", event.permalinkUrl().toString())
                .add("status", event.status())
                .add("refunds", Json.createArrayBuilder());
        return Json.createObjectBuilder()
                .add("type", event.type().jsonValue())
                .add("data", dataBuilder)
                .build()
                .toString();
    }

    private String sign(String payload, Instant timestamp) {
        var mac = newMac();
        var body = (timestamp.toString() + "\n" + payload).getBytes(StandardCharsets.UTF_8);
        var signature = mac.doFinal(body);
        return SIGNATURE_ENCODER.encodeToString(signature);
    }

    private Mac newMac() {
        try {
            var mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            return mac;
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Failed to initialize webhook signature", e);
        }
    }
}
