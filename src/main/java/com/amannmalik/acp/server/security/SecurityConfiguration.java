package com.amannmalik.acp.server.security;

import com.amannmalik.acp.util.Ensure;

import java.security.PublicKey;
import java.time.Duration;
import java.util.*;

public record SecurityConfiguration(Set<String> bearerTokens, Map<String, SigningKey> signingKeys, Duration maxTimestampSkew) {
    public SecurityConfiguration {
        bearerTokens = normalizeTokens(bearerTokens);
        signingKeys = normalizeSigningKeys(signingKeys);
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

    private static Map<String, SigningKey> normalizeSigningKeys(Map<String, SigningKey> keys) {
        var source = Ensure.notNull("security.signing_keys", keys);
        var result = new LinkedHashMap<String, SigningKey>(source.size());
        source.forEach((keyId, signingKey) -> {
            var trimmedKeyId = Ensure.nonBlank("security.signing_key.key_id", keyId).trim();
            if (result.containsKey(trimmedKeyId)) {
                throw new IllegalArgumentException("Duplicate signing key id: " + trimmedKeyId);
            }
            result.put(trimmedKeyId, copySigningKey(signingKey));
        });
        return Map.copyOf(result);
    }

    private static SigningKey copySigningKey(SigningKey signingKey) {
        signingKey = Ensure.notNull("security.signing_key", signingKey);
        if (signingKey instanceof SigningKey.HmacSha256 hmac) {
            return new SigningKey.HmacSha256(hmac.secret());
        }
        if (signingKey instanceof SigningKey.Ed25519 ed25519) {
            return ed25519;
        }
        throw new IllegalArgumentException("Unsupported signing key type: " + signingKey);
    }

    private static Duration normalizeSkew(Duration skew) {
        skew = Ensure.notNull("security.max_timestamp_skew", skew);
        if (skew.isNegative() || skew.isZero()) {
            throw new IllegalArgumentException("security.max_timestamp_skew MUST be > PT0S");
        }
        return skew;
    }

    public boolean signatureRequired() {
        return !signingKeys.isEmpty();
    }

    public sealed interface SigningKey permits SigningKey.HmacSha256, SigningKey.Ed25519 {
        Algorithm algorithm();

        enum Algorithm {
            HMAC_SHA256,
            ED25519
        }

        record HmacSha256(byte[] secret) implements SigningKey {
            public HmacSha256 {
                secret = copySecret(secret);
            }

            private static byte[] copySecret(byte[] secret) {
                var material = Ensure.notNull("security.hmac_secret.material", secret);
                if (material.length < 16) {
                    throw new IllegalArgumentException("HMAC secret MUST be at least 16 bytes");
                }
                return material.clone();
            }

            @Override
            public Algorithm algorithm() {
                return Algorithm.HMAC_SHA256;
            }

            @Override
            public byte[] secret() {
                return secret.clone();
            }
        }

        record Ed25519(PublicKey publicKey) implements SigningKey {
            public Ed25519 {
                publicKey = Ensure.notNull("security.ed25519.public_key", publicKey);
            }

            @Override
            public Algorithm algorithm() {
                return Algorithm.ED25519;
            }
        }
    }
}
