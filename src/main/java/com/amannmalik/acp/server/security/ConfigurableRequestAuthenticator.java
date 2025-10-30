package com.amannmalik.acp.server.security;

import com.amannmalik.acp.server.HttpProblem;
import com.amannmalik.acp.api.shared.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Locale;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

public final class ConfigurableRequestAuthenticator implements RequestAuthenticator {
    private static final Base64.Decoder SIGNATURE_DECODER = Base64.getUrlDecoder();

    private final SecurityConfiguration configuration;
    private final Clock clock;

    public ConfigurableRequestAuthenticator(SecurityConfiguration configuration, Clock clock) {
        this.configuration = configuration;
        this.clock = clock == null ? Clock.systemUTC() : clock;
    }

    @Override
    public void authenticate(HttpServletRequest request, byte[] body) {
        var bearer = extractBearerToken(request);
        if (!configuration.bearerTokens().contains(bearer)) {
            throw new HttpProblem(
                    401,
                    ErrorResponse.ErrorType.INVALID_REQUEST,
                    "unauthorized",
                    "Authorization token is invalid");
        }
        if (!configuration.signatureRequired()) {
            return;
        }
        var timestampHeader = headerValue(request, "Timestamp");
        if (timestampHeader == null) {
            throw new HttpProblem(
                    400,
                    ErrorResponse.ErrorType.INVALID_REQUEST,
                    "missing_timestamp",
                    "Timestamp header is required when signatures are enabled");
        }
        var timestamp = parseTimestamp(timestampHeader);
        validateSkew(timestamp);
        var signatureHeader = headerValue(request, "Signature");
        if (signatureHeader == null) {
            throw new HttpProblem(
                    400,
                    ErrorResponse.ErrorType.INVALID_REQUEST,
                    "missing_signature",
                    "Signature header is required when signatures are enabled");
        }
        var signatureParts = signatureHeader.split(":", 2);
        if (signatureParts.length != 2) {
            throw new HttpProblem(
                    400,
                    ErrorResponse.ErrorType.INVALID_REQUEST,
                    "invalid_signature",
                    "Signature header MUST be in keyId:signature format");
        }
        var keyId = signatureParts[0].trim();
        var encodedSignature = signatureParts[1].trim();
        var secret = configuration.hmacSecrets().get(keyId);
        if (secret == null) {
            throw new HttpProblem(
                    400,
                    ErrorResponse.ErrorType.INVALID_REQUEST,
                    "unknown_signature_key",
                    "Signature key id is not recognized");
        }
        var canonicalJson = CanonicalJson.canonicalize(body);
        var payload = (timestampHeader + "." + canonicalJson).getBytes(StandardCharsets.UTF_8);
        var providedSignature = decodeSignature(encodedSignature);
        var expectedSignature = hmacSha256(secret, payload);
        if (!MessageDigest.isEqual(expectedSignature, providedSignature)) {
            throw new HttpProblem(
                    401,
                    ErrorResponse.ErrorType.INVALID_REQUEST,
                    "invalid_signature",
                    "Signature verification failed");
        }
    }

    private String extractBearerToken(HttpServletRequest request) {
        var authorization = headerValue(request, "Authorization");
        if (authorization == null || authorization.isBlank()) {
            throw new HttpProblem(
                    401,
                    ErrorResponse.ErrorType.INVALID_REQUEST,
                    "unauthorized",
                    "Authorization header is required");
        }
        if (!authorization.toLowerCase(Locale.ROOT).startsWith("bearer ")) {
            throw new HttpProblem(
                    401,
                    ErrorResponse.ErrorType.INVALID_REQUEST,
                    "unauthorized",
                    "Authorization header MUST use Bearer scheme");
        }
        var token = authorization.substring(7).trim();
        if (token.isEmpty()) {
            throw new HttpProblem(
                    401,
                    ErrorResponse.ErrorType.INVALID_REQUEST,
                    "unauthorized",
                    "Bearer token MUST be non-blank");
        }
        return token;
    }

    private static String headerValue(HttpServletRequest request, String header) {
        var value = request.getHeader(header);
        if (value == null) {
            return null;
        }
        var trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private Instant parseTimestamp(String timestamp) {
        try {
            return Instant.parse(timestamp);
        } catch (Exception e) {
            throw new HttpProblem(
                    400,
                    ErrorResponse.ErrorType.INVALID_REQUEST,
                    "invalid_timestamp",
                    "Timestamp MUST be an RFC 3339 instant");
        }
    }

    private void validateSkew(Instant timestamp) {
        var now = clock.instant();
        var skew = Duration.between(timestamp, now).abs();
        if (skew.compareTo(configuration.maxTimestampSkew()) > 0) {
            throw new HttpProblem(
                    401,
                    ErrorResponse.ErrorType.INVALID_REQUEST,
                    "timestamp_out_of_window",
                    "Timestamp outside allowed skew window");
        }
    }

    private static byte[] decodeSignature(String encoded) {
        try {
            return SIGNATURE_DECODER.decode(encoded);
        } catch (IllegalArgumentException e) {
            throw new HttpProblem(
                    400,
                    ErrorResponse.ErrorType.INVALID_REQUEST,
                    "invalid_signature",
                    "Signature MUST be base64url encoded");
        }
    }

    private static byte[] hmacSha256(byte[] secret, byte[] payload) {
        try {
            var mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            return mac.doFinal(payload);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Failed to verify signature", e);
        }
    }
}
