module acp.main {
    requires transitive info.picocli;
    requires transitive jakarta.json;
    requires jakarta.servlet;
    requires java.net.http;
    requires org.eclipse.jetty.ee10.servlet;
    requires org.eclipse.jetty.server;
    requires org.bouncycastle.provider;
    requires org.bouncycastle.pkix;
    exports com.amannmalik.acp.cli;
}
