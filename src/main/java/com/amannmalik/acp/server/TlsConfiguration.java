package com.amannmalik.acp.server;

import com.amannmalik.acp.util.Ensure;

import java.nio.file.Path;
import java.util.Arrays;

public record TlsConfiguration(Path keyStorePath, char[] keyStorePassword, char[] keyPassword, String keyStoreType, int port) {
    private static final String DEFAULT_KEYSTORE_TYPE = "PKCS12";

    public TlsConfiguration {
        keyStorePath = Ensure.notNull("tls.keystore_path", keyStorePath).toAbsolutePath().normalize();
        keyStorePassword = copySecret("tls.keystore_password", keyStorePassword);
        keyPassword = keyPassword == null ? null : copySecret("tls.key_password", keyPassword);
        keyStoreType = keyStoreType == null || keyStoreType.isBlank() ? DEFAULT_KEYSTORE_TYPE : keyStoreType;
        if (port < 0) {
            throw new IllegalArgumentException("tls.port MUST be >= 0");
        }
    }

    private static char[] copySecret(String field, char[] secret) {
        var value = Ensure.notNull(field, secret);
        if (value.length == 0) {
            throw new IllegalArgumentException(field + " MUST NOT be empty");
        }
        return Arrays.copyOf(value, value.length);
    }

    public String keyStorePasswordValue() {
        return new String(keyStorePassword);
    }

    public String keyPasswordValue() {
        return keyPassword == null ? null : new String(keyPassword);
    }
}
