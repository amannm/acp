package com.amannmalik.acp.testsuite.server;

import com.amannmalik.acp.api.shared.MinorUnitAmount;
import com.amannmalik.acp.server.webhook.HttpOrderWebhookPublisher;
import com.amannmalik.acp.spi.webhook.OrderWebhookEvent;
import com.amannmalik.acp.spi.webhook.OrderWebhookPublisher;
import jakarta.json.Json;
import org.junit.jupiter.api.Test;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import javax.net.ssl.*;
import java.io.StringReader;
import java.net.*;
import java.net.http.*;
import java.net.http.HttpResponse.BodyHandler;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.NoSuchAlgorithmException;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

final class HttpOrderWebhookPublisherTest {
    private static final byte[] SECRET = Base64.getUrlDecoder().decode("c2VjcmV0X3Rlc3Rfc2VjcmV0MTIzNDU2");

    private static String expectedSignature(String payload) {
        try {
            var mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(SECRET, "HmacSHA256"));
            var signature = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(signature);
        } catch (Exception e) {
            throw new IllegalStateException("Unable to compute expected signature", e);
        }
    }

    @Test
    void publishSerializesOrderUpdateWithRefunds() {
        var clock = Clock.fixed(Instant.parse("2025-10-30T18:00:00Z"), ZoneOffset.UTC);
        var client = new RecordingHttpClient();
        OrderWebhookPublisher publisher = new HttpOrderWebhookPublisher(
                client,
                URI.create("https://example.com/webhook"),
                "Merchant-Signature",
                SECRET,
                clock,
                () -> "req-webhook-test");

        var event = new OrderWebhookEvent(
                OrderWebhookEvent.Type.ORDER_UPDATE,
                "csn_123",
                OrderWebhookEvent.OrderStatus.SHIPPED,
                URI.create("https://merchant.example.com/orders/ord_123"),
                List.of(new OrderWebhookEvent.Refund(
                        OrderWebhookEvent.RefundType.ORIGINAL_PAYMENT,
                        new MinorUnitAmount(150))));

        publisher.publish(event);

        assertNotNull(client.recordedRequest);
        assertFalse(client.recordedBody.isBlank());
        var signature = client.recordedRequest.headers().firstValue("Merchant-Signature").orElseThrow();
        assertFalse(signature.isBlank());
        var timestamp = client.recordedRequest.headers().firstValue("Timestamp").orElseThrow();
        assertEquals(clock.instant().toString(), timestamp);
        var requestId = client.recordedRequest.headers().firstValue("Request-Id").orElseThrow();
        assertEquals("req-webhook-test", requestId);

        var json = Json.createReader(new StringReader(client.recordedBody)).readObject();
        assertEquals("order_update", json.getString("type"));
        var data = json.getJsonObject("data");
        assertEquals("order", data.getString("type"));
        assertEquals("csn_123", data.getString("checkout_session_id"));
        assertEquals("https://merchant.example.com/orders/ord_123", data.getString("permalink_url"));
        assertEquals("shipped", data.getString("status"));
        var refunds = data.getJsonArray("refunds");
        assertEquals(1, refunds.size());
        var refund = refunds.getJsonObject(0);
        assertEquals("original_payment", refund.getString("type"));
        assertEquals(150, refund.getInt("amount"));
        assertEquals(
                expectedSignature(client.recordedBody),
                signature);
    }

    @Test
    void publishThrowsWhenEndpointReturnsErrorStatus() {
        var clock = Clock.fixed(Instant.parse("2025-10-30T18:00:00Z"), ZoneOffset.UTC);
        var client = new RecordingHttpClient(503);
        OrderWebhookPublisher publisher = new HttpOrderWebhookPublisher(
                client,
                URI.create("https://example.com/webhook"),
                "Merchant-Signature",
                SECRET,
                clock,
                () -> "req-webhook-error");

        var event = new OrderWebhookEvent(
                OrderWebhookEvent.Type.ORDER_CREATE,
                "csn_999",
                OrderWebhookEvent.OrderStatus.CREATED,
                URI.create("https://merchant.example.com/orders/ord_999"),
                List.of());

        var exception = assertThrows(IllegalStateException.class, () -> publisher.publish(event));
        assertEquals("Webhook endpoint responded with HTTP 503", exception.getMessage());
    }

    private static final class RecordingHttpClient extends HttpClient {
        private final int statusCode;
        private HttpRequest recordedRequest;
        private String recordedBody;

        private RecordingHttpClient() {
            this(200);
        }

        private RecordingHttpClient(int statusCode) {
            this.statusCode = statusCode;
        }

        @Override
        public Optional<CookieHandler> cookieHandler() {
            return Optional.empty();
        }

        @Override
        public Optional<Duration> connectTimeout() {
            return Optional.empty();
        }

        @Override
        public Redirect followRedirects() {
            return Redirect.NEVER;
        }

        @Override
        public Optional<ProxySelector> proxy() {
            return Optional.empty();
        }

        @Override
        public SSLContext sslContext() {
            try {
                return SSLContext.getDefault();
            } catch (NoSuchAlgorithmException e) {
                throw new IllegalStateException(e);
            }
        }

        @Override
        public SSLParameters sslParameters() {
            return sslContext().getDefaultSSLParameters();
        }

        @Override
        public Optional<Authenticator> authenticator() {
            return Optional.empty();
        }

        @Override
        public HttpClient.Version version() {
            return HttpClient.Version.HTTP_1_1;
        }

        @Override
        public Optional<Executor> executor() {
            return Optional.empty();
        }

        @Override
        public <T> HttpResponse<T> send(HttpRequest request, BodyHandler<T> responseBodyHandler)
                throws InterruptedException {
            this.recordedRequest = request;
            this.recordedBody = readBody(request.bodyPublisher().orElseThrow());
            var subscriber = responseBodyHandler.apply(new ResponseInfoImpl(statusCode));
            subscriber.onSubscribe(new NoopSubscription());
            subscriber.onNext(List.of());
            subscriber.onComplete();
            return new SimpleHttpResponse<>(request, statusCode);
        }

        @Override
        public <T> CompletableFuture<HttpResponse<T>> sendAsync(
                HttpRequest request, BodyHandler<T> responseBodyHandler) {
            return CompletableFuture.failedFuture(new UnsupportedOperationException("sendAsync"));
        }

        @Override
        public <T> CompletableFuture<HttpResponse<T>> sendAsync(
                HttpRequest request,
                BodyHandler<T> responseBodyHandler,
                HttpResponse.PushPromiseHandler<T> pushPromiseHandler) {
            return CompletableFuture.failedFuture(new UnsupportedOperationException("sendAsync"));
        }

        private String readBody(HttpRequest.BodyPublisher publisher) throws InterruptedException {
            var latch = new CountDownLatch(1);
            var buffer = new StringBuilder();
            final var error = new Throwable[1];
            publisher.subscribe(new Flow.Subscriber<>() {
                @Override
                public void onSubscribe(Flow.Subscription subscription) {
                    subscription.request(Long.MAX_VALUE);
                }

                @Override
                public void onNext(ByteBuffer item) {
                    var bytes = new byte[item.remaining()];
                    item.get(bytes);
                    buffer.append(new String(bytes, StandardCharsets.UTF_8));
                }

                @Override
                public void onError(Throwable throwable) {
                    error[0] = throwable;
                    latch.countDown();
                }

                @Override
                public void onComplete() {
                    latch.countDown();
                }
            });
            latch.await();
            if (error[0] != null) {
                throw new IllegalStateException("Failed to read request body", error[0]);
            }
            return buffer.toString();
        }
    }

    private record ResponseInfoImpl(int statusCode) implements HttpResponse.ResponseInfo {

        @Override
        public HttpHeaders headers() {
            return HttpHeaders.of(Map.of(), (a, b) -> true);
        }

        @Override
        public HttpClient.Version version() {
            return HttpClient.Version.HTTP_1_1;
        }
    }

    private static final class NoopSubscription implements Flow.Subscription {
        @Override
        public void request(long n) {
        }

        @Override
        public void cancel() {
        }
    }

    private record SimpleHttpResponse<T>(HttpRequest request, int statusCode) implements HttpResponse<T> {

        @Override
        public Optional<HttpResponse<T>> previousResponse() {
            return Optional.empty();
        }

        @Override
        public HttpHeaders headers() {
            return HttpHeaders.of(Map.of(), (a, b) -> true);
        }

        @Override
        public T body() {
            return null;
        }

        @Override
        public Optional<SSLSession> sslSession() {
            return Optional.empty();
        }

        @Override
        public URI uri() {
            return request.uri();
        }

        @Override
        public HttpClient.Version version() {
            return HttpClient.Version.HTTP_1_1;
        }
    }
}
