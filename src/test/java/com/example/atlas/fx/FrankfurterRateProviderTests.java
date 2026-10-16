package com.example.atlas.fx;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.math.BigDecimal;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;

class FrankfurterRateProviderTests {
    @Test void parsesReferenceRatesAndRejectsFutureOrInvalidValues() throws Exception {
        var response = new AtomicReference<>("{\"date\":\"2026-09-25\",\"base\":\"GBP\",\"quote\":\"KZT\",\"rate\":600.25}");
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/rate/GBP/KZT", exchange -> {
            var bytes = response.get().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes); exchange.close();
        });
        server.start();
        try {
            var provider = new FrankfurterRateProvider("http://127.0.0.1:" + server.getAddress().getPort() + "/");
            var day = LocalDate.of(2026, 9, 26);
            var rate = provider.fetch("GBP", "KZT", day);
            assertEquals(new BigDecimal("600.25"), rate.value());
            assertEquals(day.minusDays(1), rate.date());
            response.set(response.get().replace("2026-09-25", "2026-09-27"));
            assertThrows(IllegalStateException.class, () -> provider.fetch("GBP", "KZT", day));
            response.set("{\"date\":\"2026-09-25\",\"base\":\"GBP\",\"quote\":\"USD\",\"rate\":-1}");
            assertThrows(IllegalStateException.class, () -> provider.fetch("GBP", "KZT", day));
        } finally { server.stop(0); }
    }
}
