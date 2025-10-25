package com.amannmalik.acp.server;

import com.amannmalik.acp.api.checkout.CheckoutSessionService;
import com.amannmalik.acp.codec.CheckoutSessionJsonCodec;

import org.eclipse.jetty.ee10.servlet.ServletContextHandler;
import org.eclipse.jetty.ee10.servlet.ServletHolder;
import org.eclipse.jetty.server.Server;

public final class JettyHttpServer implements AutoCloseable {
    private final Server server;

    public JettyHttpServer(int port, CheckoutSessionService checkoutSessionService) {
        var codec = new CheckoutSessionJsonCodec();
        this.server = new Server(port);
        var context = new ServletContextHandler(ServletContextHandler.NO_SESSIONS);
        context.setContextPath("/");
        context.addServlet(new ServletHolder(new CheckoutSessionServlet(checkoutSessionService, codec)), "/checkout_sessions/*");
        server.setHandler(context);
    }

    public void start() throws Exception {
        server.start();
    }

    public void join() throws InterruptedException {
        server.join();
    }

    public int port() {
        return java.util.Arrays.stream(server.getConnectors())
                .filter(connector -> connector instanceof org.eclipse.jetty.server.ServerConnector)
                .map(connector -> (org.eclipse.jetty.server.ServerConnector) connector)
                .findFirst()
                .map(org.eclipse.jetty.server.ServerConnector::getLocalPort)
                .orElseThrow();
    }

    @Override
    public void close() {
        try {
            server.stop();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to stop Jetty", e);
        }
    }
}
