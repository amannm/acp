module acp.main {
    requires transitive info.picocli;
    requires transitive jakarta.json;
    requires transitive jakarta.servlet;
    requires transitive java.net.http;
    requires org.eclipse.jetty.ee10.servlet;
    requires org.eclipse.jetty.server;
    requires org.bouncycastle.provider;
    requires org.bouncycastle.pkix;
    exports com.amannmalik.acp.cli;
    exports com.amannmalik.acp.api.checkout;
    exports com.amannmalik.acp.api.checkout.model;
    exports com.amannmalik.acp.api.delegatepayment;
    exports com.amannmalik.acp.api.delegatepayment.model;
    exports com.amannmalik.acp.api.shared;
    exports com.amannmalik.acp.server;
    exports com.amannmalik.acp.server.security;
    exports com.amannmalik.acp.server.webhook;
    exports com.amannmalik.acp.util;
    exports com.amannmalik.acp.codec;
    exports com.amannmalik.acp.spi.webhook;
}
