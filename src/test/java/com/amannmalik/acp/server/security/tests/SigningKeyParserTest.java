package com.amannmalik.acp.server.security.tests;

import com.amannmalik.acp.server.security.SigningKeyParser;
import com.amannmalik.acp.server.security.SecurityConfiguration.SigningKey;
import org.junit.jupiter.api.Test;

import java.security.KeyPairGenerator;
import java.util.Base64;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

final class SigningKeyParserTest {
    @Test
    void parseSupportsHmacAndEd25519() throws Exception {
        var keyPair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        var publicKeyMaterial = Base64.getUrlEncoder().withoutPadding().encodeToString(keyPair.getPublic().getEncoded());
        var hmacSecret = Base64.getUrlEncoder().withoutPadding().encodeToString("1234567890123456".getBytes());

        var parsed = SigningKeyParser.parse(List.of(
                "hmac=hmac-sha256:" + hmacSecret,
                "ed=ed25519:" + publicKeyMaterial));

        var hmac = (SigningKey.HmacSha256) parsed.get("hmac");
        assertArrayEquals(Base64.getUrlDecoder().decode(hmacSecret), hmac.secret());

        var ed25519 = (SigningKey.Ed25519) parsed.get("ed");
        assertEquals(keyPair.getPublic(), ed25519.publicKey());
    }

    @Test
    void parseRejectsUnsupportedAlgorithm() {
        assertThrows(IllegalArgumentException.class, () -> SigningKeyParser.parse(List.of("id=unknown:abcd")));
    }
}
