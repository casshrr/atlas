package com.example.atlas.desktop;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

/** Checks the actual desktop HTTP/JSON adapter against a running backend. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
    "spring.datasource.url=${TEST_DB_URL:jdbc:postgresql://127.0.0.1:55439/atlas_test}",
    "spring.datasource.username=${TEST_DB_USER:ernur}", "spring.datasource.password=${TEST_DB_PASSWORD:}"
})
class AtlasApiClientTests {
    @LocalServerPort int port;

    @Test void desktopRoundTripAndReadableErrors() throws Exception {
        var client = new AtlasApiClient("http://127.0.0.1:" + port);
        var created = client.request("POST", "api/expenses", Map.of("amount", new BigDecimal("3.25"),
                "currency", "GBP", "category", "Other", "spentOn", LocalDate.now().toString()));
        String id = created.path("id").asText();
        try {
            assertEquals("GBP", client.request("GET", "api/expenses/" + id, null).path("currency").asText());
            var page = client.request("GET", "api/expenses?category=Other", null);
            assertEquals(1, page.path("totalElements").asInt());
            var overview = client.loadOverview(0, "category=Other", false, "GBP");
            assertEquals(1, overview.expensePage().path("totalElements").asInt());
            assertEquals("GBP", overview.currencyTotals().get(0).path("currency").asText());
            assertNull(overview.convertedTotal());
            var converted = client.loadOverview(0, "category=Other", true, "GBP");
            assertTrue(converted.convertedTotal().path("complete").asBoolean());
            assertEquals(0, new BigDecimal("3.25").compareTo(
                    new BigDecimal(converted.convertedTotal().path("amount").asText())));
            var invalid = assertThrows(IllegalStateException.class,
                    () -> client.request("POST", "api/expenses", Map.of()));
            assertTrue(invalid.getMessage().contains("amount"));
            var missing = assertThrows(IllegalStateException.class,
                    () -> client.request("GET", "api/expenses/" + UUID.randomUUID(), null));
            assertTrue(missing.getMessage().contains("not found"));
        } finally {
            client.request("DELETE", "api/expenses/" + id, null);
        }
    }
}
