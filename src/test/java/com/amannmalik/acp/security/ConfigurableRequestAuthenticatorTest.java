package com.amannmalik.acp.security;

import com.amannmalik.acp.api.shared.ErrorResponse;
import com.amannmalik.acp.server.HttpProblem;
import com.amannmalik.acp.server.security.ConfigurableRequestAuthenticator;
import com.amannmalik.acp.server.security.SecurityConfiguration;
import jakarta.servlet.http.HttpServletRequest;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.Signature;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.Set;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

final class ConfigurableRequestAuthenticatorTest {
    private static final byte[] SECRET = decode("c2VjcmV0X3Rlc3Rfc2VjcmV0MTIzNDU2");

    @Test
    @DisplayName("authenticate accepts valid bearer + signature")
    void authenticateValidRequest() {
        var configuration = new SecurityConfiguration(
                Set.of("token"),
                Map.of("key1", new SecurityConfiguration.SigningKey.HmacSha256(SECRET)),
                java.time.Duration.ofMinutes(5));
        var authenticator = new ConfigurableRequestAuthenticator(configuration, Clock.fixed(Instant.parse("2025-10-30T12:00:00Z"), java.time.ZoneOffset.UTC));
        var timestamp = "2025-10-30T12:00:00Z";
        var body = "{\"items\":[]}".getBytes(StandardCharsets.UTF_8);
        var signature = sign(timestamp, body);
        var request = request(Map.of(
                "Authorization", "Bearer token",
                "Timestamp", timestamp,
                "Signature", "key1:" + signature));

        authenticator.authenticate(request, body);
    }

    @Test
    @DisplayName("authenticate rejects missing timestamp when signature required")
    void authenticateMissingTimestamp() {
        var configuration = new SecurityConfiguration(
                Set.of("token"),
                Map.of("key1", new SecurityConfiguration.SigningKey.HmacSha256(SECRET)),
                java.time.Duration.ofMinutes(5));
        var authenticator = new ConfigurableRequestAuthenticator(configuration, Clock.systemUTC());
        var body = "{\"items\":[]}".getBytes(StandardCharsets.UTF_8);
        var request = request(Map.of(
                "Authorization", "Bearer token",
                "Signature", "key1:abc"));

        var problem = assertThrows(HttpProblem.class, () -> authenticator.authenticate(request, body));
        assertEquals("missing_timestamp", problem.code());
        assertEquals(ErrorResponse.ErrorType.INVALID_REQUEST, problem.errorType());
    }

    @Test
    @DisplayName("authenticate rejects invalid signature")
    void authenticateInvalidSignature() {
        var configuration = new SecurityConfiguration(
                Set.of("token"),
                Map.of("key1", new SecurityConfiguration.SigningKey.HmacSha256(SECRET)),
                java.time.Duration.ofMinutes(5));
        var authenticator = new ConfigurableRequestAuthenticator(configuration, Clock.systemUTC());
        var timestamp = Instant.now().toString();
        var body = "{\"items\":[]}".getBytes(StandardCharsets.UTF_8);
        var request = request(Map.of(
                "Authorization", "Bearer token",
                "Timestamp", timestamp,
                "Signature", "key1:invalid"));

        var problem = assertThrows(HttpProblem.class, () -> authenticator.authenticate(request, body));
        assertEquals("invalid_signature", problem.code());
        assertEquals(ErrorResponse.ErrorType.INVALID_REQUEST, problem.errorType());
    }

    @Test
    @DisplayName("authenticate accepts canonicalized JSON bodies")
    void authenticateCanonicalizedPayload() {
        var configuration = new SecurityConfiguration(
                Set.of("token"),
                Map.of("key1", new SecurityConfiguration.SigningKey.HmacSha256(SECRET)),
                java.time.Duration.ofMinutes(5));
        var authenticator = new ConfigurableRequestAuthenticator(configuration, Clock.systemUTC());
        var timestamp = Instant.now().toString();
        var body = """
                {
                  "b": 2,
                  "items": [
                    { "sku": "123", "quantity": 1 }
                  ],
                  "a": { "last": "Smith", "first": "John" }
                }
                """.getBytes(StandardCharsets.UTF_8);
        var canonical = "{\"a\":{\"first\":\"John\",\"last\":\"Smith\"},\"b\":2,\"items\":[{\"quantity\":1,\"sku\":\"123\"}]}";
        var signature = signCanonical(timestamp, canonical);
        var request = request(Map.of(
                "Authorization", "Bearer token",
                "Timestamp", timestamp,
                "Signature", "key1:" + signature));

        authenticator.authenticate(request, body);
    }

    @Test
    @DisplayName("authenticate accepts valid Ed25519 signature")
    void authenticateValidEd25519Signature() throws Exception {
        var keyPair = ed25519KeyPair();
        var configuration = new SecurityConfiguration(
                Set.of("token"),
                Map.of("ed", new SecurityConfiguration.SigningKey.Ed25519(keyPair.getPublic())),
                java.time.Duration.ofMinutes(5));
        var clock = Clock.fixed(Instant.parse("2025-10-30T12:00:00Z"), java.time.ZoneOffset.UTC);
        var authenticator = new ConfigurableRequestAuthenticator(configuration, clock);
        var timestamp = clock.instant().toString();
        var body = "{\"items\":[]}".getBytes(StandardCharsets.UTF_8);
        var signature = signEd25519(timestamp, body, keyPair.getPrivate());
        var request = request(Map.of(
                "Authorization", "Bearer token",
                "Timestamp", timestamp,
                "Signature", "ed:" + signature));

        authenticator.authenticate(request, body);
    }

    @Test
    @DisplayName("authenticate rejects invalid Ed25519 signature")
    void authenticateInvalidEd25519Signature() throws Exception {
        var keyPair = ed25519KeyPair();
        var configuration = new SecurityConfiguration(
                Set.of("token"),
                Map.of("ed", new SecurityConfiguration.SigningKey.Ed25519(keyPair.getPublic())),
                java.time.Duration.ofMinutes(5));
        var authenticator = new ConfigurableRequestAuthenticator(configuration, Clock.systemUTC());
        var timestamp = Instant.now().toString();
        var body = "{\"items\":[]}".getBytes(StandardCharsets.UTF_8);
        var signature = signEd25519(timestamp, body, keyPair.getPrivate());
        var tampered = signature.substring(0, signature.length() - 2) + "AA";
        var request = request(Map.of(
                "Authorization", "Bearer token",
                "Timestamp", timestamp,
                "Signature", "ed:" + tampered));

        var problem = assertThrows(HttpProblem.class, () -> authenticator.authenticate(request, body));
        assertEquals("invalid_signature", problem.code());
        assertEquals(ErrorResponse.ErrorType.INVALID_REQUEST, problem.errorType());
    }

    private static HttpServletRequest request(Map<String, String> headers) {
        return (HttpServletRequest) Proxy.newProxyInstance(
                HttpServletRequest.class.getClassLoader(),
                new Class<?>[] {HttpServletRequest.class},
                (proxy, method, args) -> {
                    if ("getHeader".equals(method.getName())) {
                        var name = (String) args[0];
                        return headers.get(name);
                    }
                    throw new UnsupportedOperationException("Method not implemented: " + method.getName());
                });
    }

    private static String sign(String timestamp, byte[] body) {
        try {
            var mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(SECRET, "HmacSHA256"));
            var canonical = timestamp + "." + new String(body, StandardCharsets.UTF_8);
            var digest = mac.doFinal(canonical.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String signCanonical(String timestamp, String canonicalJson) {
        try {
            var mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(SECRET, "HmacSHA256"));
            var digest = mac.doFinal((timestamp + "." + canonicalJson).getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String signEd25519(String timestamp, byte[] body, PrivateKey privateKey) throws GeneralSecurityException {
        var payload = (timestamp + "." + new String(body, StandardCharsets.UTF_8)).getBytes(StandardCharsets.UTF_8);
        var signature = Signature.getInstance("Ed25519");
        signature.initSign(privateKey);
        signature.update(payload);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(signature.sign());
    }

    private static KeyPair ed25519KeyPair() throws GeneralSecurityException {
        var generator = KeyPairGenerator.getInstance("Ed25519");
        return generator.generateKeyPair();
    }

    private static byte[] decode(String value) {
        return Base64.getDecoder().decode(value);
    }
}
