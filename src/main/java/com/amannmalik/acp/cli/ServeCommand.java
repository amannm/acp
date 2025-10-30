package com.amannmalik.acp.cli;

import com.amannmalik.acp.api.checkout.InMemoryCheckoutSessionService;
import com.amannmalik.acp.api.delegatepayment.InMemoryDelegatePaymentService;
import com.amannmalik.acp.api.shared.CurrencyCode;
import com.amannmalik.acp.server.JettyHttpServer;
import com.amannmalik.acp.server.security.ConfigurableRequestAuthenticator;
import com.amannmalik.acp.server.security.RequestAuthenticator;
import com.amannmalik.acp.server.security.SecurityConfiguration;
import com.amannmalik.acp.server.webhook.HttpOrderWebhookPublisher;
import com.amannmalik.acp.spi.webhook.OrderWebhookPublisher;

import picocli.CommandLine;

import java.time.Clock;
import java.time.Duration;
import java.net.URI;
import java.net.http.HttpClient;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;

@CommandLine.Command(name = "serve", description = "Start the Agentic Commerce Protocol reference server")
public final class ServeCommand implements Callable<Integer> {
    public ServeCommand() {}

    @CommandLine.Option(names = "--port", defaultValue = "8080", description = "TCP port to bind (default: ${DEFAULT-VALUE})")
    int port;

    @CommandLine.Option(
            names = "--price",
            split = ",",
            description = "Static price override(s) in the form item_id=amount_minor_units")
    List<String> priceOverrides;

    @CommandLine.Option(
            names = "--auth-token",
            required = true,
            description = "Allowed bearer token(s). Repeat to configure multiple tokens.")
    List<String> authTokens;

    @CommandLine.Option(
            names = "--signature-key",
            description = "Signature key mapping keyId=base64urlSecret. Repeat per key.")
    List<String> signatureKeys;

    @CommandLine.Option(
            names = "--max-timestamp-skew",
            defaultValue = "PT5M",
            description = "Maximum allowed request timestamp skew (ISO-8601). Default: ${DEFAULT-VALUE}")
    Duration maxTimestampSkew;

    @CommandLine.Option(
            names = "--webhook-endpoint",
            description = "Order webhook endpoint URL (enables webhook publishing when provided)")
    String webhookEndpoint;

    @CommandLine.Option(
            names = "--webhook-signature-key",
            description = "Base64url secret used to sign webhook payloads")
    String webhookSignatureKey;

    @CommandLine.Option(
            names = "--webhook-signature-header",
            defaultValue = "Merchant-Signature",
            description = "Header name used for webhook signatures (default: ${DEFAULT-VALUE})")
    String webhookSignatureHeader;

    @Override
    public Integer call() throws Exception {
        var priceBook = parsePriceOverrides();
        var orderPublisher = webhookPublisher();
        var checkoutService = priceBook.isEmpty()
                ? new InMemoryCheckoutSessionService(orderPublisher)
                : new InMemoryCheckoutSessionService(priceBook, Clock.systemUTC(), new CurrencyCode("usd"), orderPublisher);
        var delegatePaymentService = new InMemoryDelegatePaymentService();
        var authenticator = authenticator();
        try (var server = new JettyHttpServer(port, checkoutService, delegatePaymentService, authenticator)) {
            server.start();
            System.out.printf("ACP server listening on http://localhost:%d%n", server.port());
            server.join();
        }
        return 0;
    }

    private Map<String, Long> parsePriceOverrides() {
        if (priceOverrides == null || priceOverrides.isEmpty()) {
            return Map.of();
        }
        var map = new LinkedHashMap<String, Long>();
        for (var entry : priceOverrides) {
            var parts = entry.split("=");
            if (parts.length != 2) {
                throw new IllegalArgumentException("Invalid price override format: " + entry);
            }
            map.put(parts[0], Long.parseLong(parts[1]));
        }
        return Map.copyOf(map);
    }

    private RequestAuthenticator authenticator() {
        var tokens = parseBearerTokens();
        var secrets = parseSignatureSecrets();
        var configuration = new SecurityConfiguration(tokens, secrets, maxTimestampSkew);
        return new ConfigurableRequestAuthenticator(configuration, Clock.systemUTC());
    }

    private Set<String> parseBearerTokens() {
        if (authTokens == null || authTokens.isEmpty()) {
            throw new IllegalArgumentException("At least one --auth-token MUST be provided");
        }
        var set = new LinkedHashSet<String>();
        for (var token : authTokens) {
            if (!set.add(token)) {
                throw new IllegalArgumentException("Duplicate --auth-token provided: " + token);
            }
        }
        return Set.copyOf(set);
    }

    private Map<String, byte[]> parseSignatureSecrets() {
        if (signatureKeys == null || signatureKeys.isEmpty()) {
            return Map.of();
        }
        var map = new LinkedHashMap<String, byte[]>();
        for (var entry : signatureKeys) {
            var parts = entry.split("=", 2);
            if (parts.length != 2) {
                throw new IllegalArgumentException("Invalid signature key format: " + entry);
            }
            var keyId = parts[0];
            if (map.containsKey(keyId)) {
                throw new IllegalArgumentException("Duplicate signature key id: " + keyId);
            }
            map.put(keyId, decodeSecret(parts[1]));
        }
        return Map.copyOf(map);
    }

    private static byte[] decodeSecret(String encoded) {
        try {
            return Base64.getUrlDecoder().decode(encoded);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Signature key MUST be base64url encoded", e);
        }
    }

    private OrderWebhookPublisher webhookPublisher() {
        if (webhookEndpoint == null && webhookSignatureKey == null) {
            return OrderWebhookPublisher.NOOP;
        }
        if (webhookEndpoint == null || webhookSignatureKey == null) {
            throw new IllegalArgumentException(
                    "--webhook-endpoint and --webhook-signature-key MUST be provided together");
        }
        var endpointUri = parseUri(webhookEndpoint);
        var secret = decodeSecret(webhookSignatureKey);
        var client = HttpClient.newHttpClient();
        return new HttpOrderWebhookPublisher(client, endpointUri, webhookSignatureHeader, secret, Clock.systemUTC());
    }

    private static URI parseUri(String value) {
        try {
            return URI.create(value);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid webhook endpoint URI: " + value, e);
        }
    }
}
