package com.amannmalik.acp.server.security;

import com.amannmalik.acp.util.Ensure;

import java.time.Duration;
import java.util.*;

public record SecurityConfiguration(Set<String> bearerTokens, Map<String, byte[]> hmacSecrets, Duration maxTimestampSkew) {
    public SecurityConfiguration {
        bearerTokens = normalizeTokens(bearerTokens);
        hmacSecrets = normalizeSecrets(hmacSecrets);
        maxTimestampSkew = normalizeSkew(maxTimestampSkew);
    }

    private static Set<String> normalizeTokens(Set<String> tokens) {
        var copy = Set.copyOf(Ensure.notNull("security.bearer_tokens", tokens));
        if (copy.isEmpty()) {
            throw new IllegalArgumentException("At least one bearer token MUST be configured");
        }
        copy.forEach(token -> Ensure.nonBlank("security.bearer_token", token));
        return copy;
    }

    private static Map<String, byte[]> normalizeSecrets(Map<String, byte[]> secrets) {
        var source = Ensure.notNull("security.hmac_secrets", secrets);
        var result = new LinkedHashMap<String, byte[]>(source.size());
        source.forEach((keyId, secret) -> {
            var trimmedKeyId = Ensure.nonBlank("security.hmac_secret.key_id", keyId).trim();
            if (result.containsKey(trimmedKeyId)) {
                throw new IllegalArgumentException("Duplicate HMAC key id: " + trimmedKeyId);
            }
            result.put(trimmedKeyId, copySecret(secret));
        });
        return Map.copyOf(result);
    }

    private static byte[] copySecret(byte[] secret) {
        var material = Ensure.notNull("security.hmac_secret.material", secret);
        if (material.length < 16) {
            throw new IllegalArgumentException("HMAC secret MUST be at least 16 bytes");
        }
        return material.clone();
    }

    private static Duration normalizeSkew(Duration skew) {
        skew = Ensure.notNull("security.max_timestamp_skew", skew);
        if (skew.isNegative() || skew.isZero()) {
            throw new IllegalArgumentException("security.max_timestamp_skew MUST be > PT0S");
        }
        return skew;
    }

    public boolean signatureRequired() {
        return !hmacSecrets.isEmpty();
    }
}
