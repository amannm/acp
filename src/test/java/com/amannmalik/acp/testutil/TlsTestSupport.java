package com.amannmalik.acp.testutil;

import com.amannmalik.acp.server.TlsConfiguration;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.util.Arrays;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.GeneralName;
import org.bouncycastle.asn1.x509.GeneralNames;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;

public final class TlsTestSupport {
    private static final char[] PASSWORD = "changeit".toCharArray();

    private TlsTestSupport() {}

    public static TestContext createTlsContext() {
        try {
            var keyPair = generateKeyPair();
            var certificate = selfSignedCertificate(keyPair);
            var keyStorePath = writeKeyStore(keyPair, certificate);
            var tlsConfiguration = new TlsConfiguration(keyStorePath, PASSWORD, null, "PKCS12", 0);
            var sslContext = sslContext(certificate);
            return new TestContext(tlsConfiguration, sslContext);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to create TLS test context", e);
        }
    }

    private static KeyPair generateKeyPair() throws Exception {
        var generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        return generator.generateKeyPair();
    }

    private static X509Certificate selfSignedCertificate(KeyPair keyPair) throws Exception {
        var now = Instant.now();
        var notBefore = java.util.Date.from(now.minusSeconds(300));
        var notAfter = java.util.Date.from(now.plusSeconds(31536000));
        var subject = new X500Name("CN=localhost");
        var serial = new BigInteger(160, new SecureRandom());
        var contentSigner = signer(keyPair);
        var builder = new JcaX509v3CertificateBuilder(
                subject,
                serial,
                notBefore,
                notAfter,
                subject,
                keyPair.getPublic());
        var subjectAltName = new GeneralNames(new GeneralName(GeneralName.dNSName, "localhost"));
        builder.addExtension(Extension.subjectAlternativeName, false, subjectAltName);
        var holder = builder.build(contentSigner);
        return new JcaX509CertificateConverter().getCertificate(holder);
    }

    private static ContentSigner signer(KeyPair keyPair) throws Exception {
        return new JcaContentSignerBuilder("SHA256withRSA").build(keyPair.getPrivate());
    }

    private static Path writeKeyStore(KeyPair keyPair, X509Certificate certificate) throws Exception {
        var keyStore = KeyStore.getInstance("PKCS12");
        keyStore.load(null, null);
        keyStore.setKeyEntry("server", keyPair.getPrivate(), PASSWORD, new Certificate[] {certificate});
        var path = Files.createTempFile("acp-test-keystore", ".p12");
        try (var output = Files.newOutputStream(path)) {
            keyStore.store(output, PASSWORD);
        }
        path.toFile().deleteOnExit();
        return path;
    }

    private static SSLContext sslContext(X509Certificate certificate) throws Exception {
        var trustStore = KeyStore.getInstance("PKCS12");
        trustStore.load(null, null);
        trustStore.setCertificateEntry("server", certificate);
        var trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        trustManagerFactory.init(trustStore);
        var sslContext = SSLContext.getInstance("TLS");
        sslContext.init(null, trustManagerFactory.getTrustManagers(), new SecureRandom());
        return sslContext;
    }

    public record TestContext(TlsConfiguration configuration, SSLContext sslContext) implements AutoCloseable {
        @Override
        public void close() {
            Arrays.fill(configuration.keyStorePassword(), '\0');
        }
    }
}
