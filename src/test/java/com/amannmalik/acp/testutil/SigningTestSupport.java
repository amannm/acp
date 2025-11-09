package com.amannmalik.acp.testutil;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.Base64;

public final class SigningTestSupport {
    private SigningTestSupport() {
    }

    public static String hmacSignature(byte[] secret, String timestamp, String canonicalJson) {
        try {
            var mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            var payload = (timestamp + "." + canonicalJson).getBytes(StandardCharsets.UTF_8);
            var digest = mac.doFinal(payload);
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Failed to compute HMAC signature", e);
        }
    }
}
