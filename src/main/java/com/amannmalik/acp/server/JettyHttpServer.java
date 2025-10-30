package com.amannmalik.acp.server;

import com.amannmalik.acp.api.checkout.CheckoutSessionService;
import com.amannmalik.acp.api.delegatepayment.DelegatePaymentService;
import com.amannmalik.acp.codec.CheckoutSessionJsonCodec;
import com.amannmalik.acp.codec.DelegatePaymentJsonCodec;

import org.eclipse.jetty.ee10.servlet.ServletContextHandler;
import org.eclipse.jetty.ee10.servlet.ServletHolder;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;

import java.util.Arrays;

public final class JettyHttpServer implements AutoCloseable {
    private final Server server;

    public JettyHttpServer(
            int port,
            CheckoutSessionService checkoutSessionService,
            DelegatePaymentService delegatePaymentService) {
        var checkoutCodec = new CheckoutSessionJsonCodec();
        var delegateCodec = new DelegatePaymentJsonCodec();
        this.server = new Server(port);
        var context = new ServletContextHandler(ServletContextHandler.NO_SESSIONS);
        context.setContextPath("/");
        context.addServlet(new ServletHolder(new CheckoutSessionServlet(checkoutSessionService, checkoutCodec)), "/checkout_sessions/*");
        context.addServlet(
                new ServletHolder(new DelegatePaymentServlet(delegatePaymentService, delegateCodec)),
                "/agentic_commerce/delegate_payment");
        server.setHandler(context);
    }

    public void start() throws Exception {
        server.start();
    }

    public void join() throws InterruptedException {
        server.join();
    }

    public int port() {
        return Arrays.stream(server.getConnectors())
                .filter(connector -> connector instanceof ServerConnector)
                .map(connector -> (ServerConnector) connector)
                .findFirst()
                .map(ServerConnector::getLocalPort)
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
