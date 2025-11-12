package com.amannmalik.acp.server.security;

import com.amannmalik.acp.server.security.SecurityConfiguration.SigningKey;
import com.amannmalik.acp.util.Ensure;

import java.security.*;
import java.security.spec.X509EncodedKeySpec;
import java.util.*;

public final class SigningKeyParser {
    private SigningKeyParser() {
    }

    public static Map<String, SigningKey> parse(List<String> specifications) {
        if (specifications == null || specifications.isEmpty()) {
            return Map.of();
        }
        var result = new LinkedHashMap<String, SigningKey>(specifications.size());
        for (var entry : specifications) {
            var parts = entry.split("=", 2);
            if (parts.length != 2) {
                throw new IllegalArgumentException("Invalid signature key format: " + entry);
            }
            var keyId = Ensure.nonBlank("signature_key.id", parts[0]).trim();
            if (result.containsKey(keyId)) {
                throw new IllegalArgumentException("Duplicate signature key id: " + keyId);
            }
            result.put(keyId, parseKey(parts[1]));
        }
        return Map.copyOf(result);
    }

    private static SigningKey parseKey(String specification) {
        var parts = specification.split(":", 2);
        var algorithm = parts.length == 2 ? parts[0].toLowerCase(Locale.ROOT) : "hmac-sha256";
        var material = parts.length == 2 ? parts[1] : parts[0];
        return switch (algorithm) {
            case "hmac", "hmac-sha256" -> new SigningKey.HmacSha256(decode(material));
            case "ed25519" -> new SigningKey.Ed25519(parseEd25519PublicKey(material));
            default -> throw new IllegalArgumentException("Unsupported signature algorithm: " + algorithm);
        };
    }

    private static byte[] decode(String encoded) {
        try {
            return Base64.getUrlDecoder().decode(encoded);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Signature key material MUST be base64url encoded", e);
        }
    }

    private static PublicKey parseEd25519PublicKey(String encoded) {
        var material = decode(encoded);
        try {
            return KeyFactory.getInstance("Ed25519").generatePublic(new X509EncodedKeySpec(material));
        } catch (GeneralSecurityException e) {
            throw new IllegalArgumentException("Invalid Ed25519 public key", e);
        }
    }
}
