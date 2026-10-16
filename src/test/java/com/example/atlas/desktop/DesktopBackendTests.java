package com.example.atlas.desktop;

import org.junit.jupiter.api.Test;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import static org.junit.jupiter.api.Assertions.*;

class DesktopBackendTests {
    @Test void ownsAndClosesItsEmbeddedBackend() throws Exception {
        String url;
        try (var backend = DesktopBackend.start(null,
                "--spring.datasource.url=" + System.getenv().getOrDefault("TEST_DB_URL", "jdbc:postgresql://127.0.0.1:55439/atlas_test"),
                "--spring.datasource.username=" + System.getenv().getOrDefault("TEST_DB_USER", "ernur"),
                "--spring.datasource.password=" + System.getenv().getOrDefault("TEST_DB_PASSWORD", ""))) {
            url = backend.url();
            assertTrue(url.startsWith("http://127.0.0.1:"));
            assertEquals(200, HttpClient.newHttpClient().send(HttpRequest.newBuilder(URI.create(url + "api/expenses")).GET().build(),
                    HttpResponse.BodyHandlers.ofString()).statusCode());
        }
        var request = HttpRequest.newBuilder(URI.create(url + "api/expenses")).timeout(Duration.ofSeconds(2)).GET().build();
        assertThrows(java.io.IOException.class, () -> HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(1)).build()
                .send(request, HttpResponse.BodyHandlers.ofString()));
    }

    @Test void externalModeDoesNotStartOrStopAServer() {
        try (var backend = DesktopBackend.start("http://example.invalid:8080/")) {
            assertEquals("http://example.invalid:8080/", backend.url());
        }
    }
}
