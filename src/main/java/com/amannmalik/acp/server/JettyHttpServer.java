package com.amannmalik.acp.server;

import com.amannmalik.acp.api.checkout.CheckoutSessionService;
import com.amannmalik.acp.api.delegatepayment.DelegatePaymentService;
import com.amannmalik.acp.codec.CheckoutSessionJsonCodec;
import com.amannmalik.acp.codec.DelegatePaymentJsonCodec;
import com.amannmalik.acp.server.security.RequestAuthenticator;

import org.eclipse.jetty.ee10.servlet.ServletContextHandler;
import org.eclipse.jetty.ee10.servlet.ServletHolder;
import org.eclipse.jetty.server.HttpConfiguration;
import org.eclipse.jetty.server.HttpConnectionFactory;
import org.eclipse.jetty.server.SecureRequestCustomizer;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.server.SslConnectionFactory;
import org.eclipse.jetty.util.ssl.SslContextFactory;

import java.util.Objects;

public final class JettyHttpServer implements AutoCloseable {
    private final Server server;
    private final ServerConnector httpConnector;
    private final ServerConnector httpsConnector;

    public JettyHttpServer(
            int port,
            CheckoutSessionService checkoutSessionService,
            DelegatePaymentService delegatePaymentService,
            RequestAuthenticator requestAuthenticator) {
        this(Configuration.httpOnly(port), checkoutSessionService, delegatePaymentService, requestAuthenticator);
    }

    public JettyHttpServer(
            Configuration configuration,
            CheckoutSessionService checkoutSessionService,
            DelegatePaymentService delegatePaymentService,
            RequestAuthenticator requestAuthenticator) {
        var checkoutCodec = new CheckoutSessionJsonCodec();
        var delegateCodec = new DelegatePaymentJsonCodec();
        this.server = new Server();
        var context = new ServletContextHandler(ServletContextHandler.NO_SESSIONS);
        context.setContextPath("/");
        context.addServlet(
                new ServletHolder(new CheckoutSessionServlet(checkoutSessionService, checkoutCodec, requestAuthenticator)),
                "/checkout_sessions/*");
        context.addServlet(
                new ServletHolder(new DelegatePaymentServlet(delegatePaymentService, delegateCodec, requestAuthenticator)),
                "/agentic_commerce/delegate_payment");
        server.setHandler(context);

        this.httpConnector = configureHttpConnector(configuration.httpPort());
        this.httpsConnector = configureHttpsConnector(configuration.tlsConfiguration());
        if (httpConnector == null && httpsConnector == null) {
            throw new IllegalArgumentException("At least one connector MUST be configured");
        }
    }

    public void start() throws Exception {
        server.start();
    }

    public void join() throws InterruptedException {
        server.join();
    }

    public int port() {
        if (httpsConnector != null) {
            return httpsConnector.getLocalPort();
        }
        if (httpConnector != null) {
            return httpConnector.getLocalPort();
        }
        throw new IllegalStateException("Server has no active connectors");
    }

    public boolean hasHttps() {
        return httpsConnector != null;
    }

    public int httpsPort() {
        if (httpsConnector == null) {
            throw new IllegalStateException("HTTPS connector not configured");
        }
        return httpsConnector.getLocalPort();
    }

    public boolean hasHttp() {
        return httpConnector != null;
    }

    public int httpPort() {
        if (httpConnector == null) {
            throw new IllegalStateException("HTTP connector not configured");
        }
        return httpConnector.getLocalPort();
    }

    @Override
    public void close() {
        try {
            server.stop();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to stop Jetty", e);
        }
    }

    private ServerConnector configureHttpConnector(Integer port) {
        if (port == null) {
            return null;
        }
        var connector = new ServerConnector(server);
        connector.setPort(port);
        server.addConnector(connector);
        return connector;
    }

    private ServerConnector configureHttpsConnector(TlsConfiguration tlsConfiguration) {
        if (tlsConfiguration == null) {
            return null;
        }
        var config = new HttpConfiguration();
        config.setSecureScheme("https");
        if (tlsConfiguration.port() > 0) {
            config.setSecurePort(tlsConfiguration.port());
        }
        config.addCustomizer(new SecureRequestCustomizer());

        var sslContextFactory = new SslContextFactory.Server();
        sslContextFactory.setKeyStorePath(tlsConfiguration.keyStorePath().toString());
        sslContextFactory.setKeyStoreType(tlsConfiguration.keyStoreType());
        sslContextFactory.setKeyStorePassword(tlsConfiguration.keyStorePasswordValue());
        var keyPassword = tlsConfiguration.keyPasswordValue();
        if (keyPassword != null) {
            sslContextFactory.setKeyManagerPassword(keyPassword);
        }

        var connector = new ServerConnector(
                server,
                new SslConnectionFactory(sslContextFactory, "http/1.1"),
                new HttpConnectionFactory(config));
        connector.setPort(tlsConfiguration.port());
        server.addConnector(connector);
        return connector;
    }

    public record Configuration(Integer httpPort, TlsConfiguration tlsConfiguration) {
        public Configuration {
            if (httpPort != null && httpPort < 0) {
                throw new IllegalArgumentException("http.port MUST be >= 0");
            }
            if (httpPort == null && tlsConfiguration == null) {
                throw new IllegalArgumentException("At least one connector MUST be configured");
            }
        }

        public static Configuration httpOnly(int port) {
            return new Configuration(port, null);
        }

        public static Configuration httpsOnly(TlsConfiguration tlsConfiguration) {
            return new Configuration(null, Objects.requireNonNull(tlsConfiguration));
        }

        public static Configuration httpAndHttps(int httpPort, TlsConfiguration tlsConfiguration) {
            return new Configuration(httpPort, Objects.requireNonNull(tlsConfiguration));
        }
    }
}
