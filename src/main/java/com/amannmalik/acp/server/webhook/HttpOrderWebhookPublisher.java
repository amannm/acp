package com.amannmalik.acp.server.webhook;

import com.amannmalik.acp.spi.webhook.OrderWebhookEvent;
import com.amannmalik.acp.spi.webhook.OrderWebhookPublisher;
import com.amannmalik.acp.util.Ensure;
import jakarta.json.Json;
import jakarta.json.JsonArrayBuilder;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.time.Clock;
import java.util.Base64;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;

public final class HttpOrderWebhookPublisher implements OrderWebhookPublisher {
    private static final Base64.Encoder SIGNATURE_ENCODER = Base64.getUrlEncoder().withoutPadding();

    private final HttpClient httpClient;
    private final URI endpoint;
    private final String signatureHeader;
    private final byte[] secret;
    private final Clock clock;
    private final Supplier<String> requestIdSupplier;

    public HttpOrderWebhookPublisher(HttpClient httpClient, URI endpoint, String signatureHeader, byte[] secret, Clock clock) {
        this(httpClient, endpoint, signatureHeader, secret, clock, HttpOrderWebhookPublisher::defaultRequestId);
    }

    public HttpOrderWebhookPublisher(
            HttpClient httpClient,
            URI endpoint,
            String signatureHeader,
            byte[] secret,
            Clock clock,
            Supplier<String> requestIdSupplier) {
        this.httpClient = Ensure.notNull("webhook.http_client", httpClient);
        this.endpoint = Ensure.notNull("webhook.endpoint", endpoint);
        this.signatureHeader = Ensure.nonBlank("webhook.signature_header", signatureHeader);
        this.secret = secret.clone();
        this.clock = clock == null ? Clock.systemUTC() : clock;
        this.requestIdSupplier =
                Objects.requireNonNullElse(requestIdSupplier, HttpOrderWebhookPublisher::defaultRequestId);
    }

    @Override
    public void publish(OrderWebhookEvent event) {
        var payload = serialize(event);
        var timestamp = clock.instant();
        var signature = sign(payload);
        var requestId = requestIdSupplier.get();
        var request = HttpRequest.newBuilder(endpoint)
                .header("Content-Type", "application/json")
                .header(signatureHeader, signature)
                .header("Request-Id", requestId)
                .header("Timestamp", timestamp.toString())
                .POST(HttpRequest.BodyPublishers.ofString(payload))
                .build();
        try {
            var response = httpClient.send(request, HttpResponse.BodyHandlers.discarding());
            var status = response.statusCode();
            if (status < 200 || status >= 300) {
                throw new IllegalStateException("Webhook endpoint responded with HTTP " + status);
            }
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
                .add("status", event.status().jsonValue())
                .add("refunds", writeRefunds(event.refunds()));
        return Json.createObjectBuilder()
                .add("type", event.type().jsonValue())
                .add("data", dataBuilder)
                .build()
                .toString();
    }

    private JsonArrayBuilder writeRefunds(List<OrderWebhookEvent.Refund> refunds) {
        var builder = Json.createArrayBuilder();
        for (var refund : refunds) {
            builder.add(Json.createObjectBuilder()
                    .add("type", refund.type().jsonValue())
                    .add("amount", refund.amount().value()));
        }
        return builder;
    }

    private String sign(String payload) {
        var mac = newMac();
        var signature = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
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

    private static String defaultRequestId() {
        return "req_" + UUID.randomUUID();
    }
}
