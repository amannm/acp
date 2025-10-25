package com.amannmalik.acp.cli;

import com.amannmalik.acp.api.checkout.InMemoryCheckoutSessionService;
import com.amannmalik.acp.api.shared.CurrencyCode;
import com.amannmalik.acp.server.JettyHttpServer;

import picocli.CommandLine;

import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

@CommandLine.Command(name = "serve", description = "Start the Agentic Commerce Protocol reference server")
public final class ServeCommand implements Callable<Integer> {
    public ServeCommand() {}

    @CommandLine.Option(names = "--port", defaultValue = "8080", description = "TCP port to bind (default: ${DEFAULT-VALUE})")
    int port;

    @CommandLine.Option(
            names = "--price",
            split = ",",
            description = "Static price override(s) in the form item_id=amount_minor_units")
    List<String> priceOverrides;

    @Override
    public Integer call() throws Exception {
        var priceBook = parsePriceOverrides();
        var service = priceBook.isEmpty()
                ? new InMemoryCheckoutSessionService()
                : new InMemoryCheckoutSessionService(priceBook, Clock.systemUTC(), new CurrencyCode("usd"));
        try (var server = new JettyHttpServer(port, service)) {
            server.start();
            System.out.printf("ACP server listening on http://localhost:%d%n", server.port());
            server.join();
        }
        return 0;
    }

    private Map<String, Long> parsePriceOverrides() {
        if (priceOverrides == null || priceOverrides.isEmpty()) {
            return Map.of();
        }
        var map = new LinkedHashMap<String, Long>();
        for (var entry : priceOverrides) {
            var parts = entry.split("=");
            if (parts.length != 2) {
                throw new IllegalArgumentException("Invalid price override format: " + entry);
            }
            map.put(parts[0], Long.parseLong(parts[1]));
        }
        return Map.copyOf(map);
    }
}
