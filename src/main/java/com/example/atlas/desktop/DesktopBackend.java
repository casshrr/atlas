package com.example.atlas.desktop;

import com.example.atlas.AtlasApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.server.context.WebServerApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import java.util.ArrayList;
import java.util.Arrays;

/** Owns only the backend started by this desktop process. Never stops an external server. */
final class DesktopBackend implements AutoCloseable {
    private final ConfigurableApplicationContext context;
    private final String url;

    private DesktopBackend(ConfigurableApplicationContext context, String url) { this.context = context; this.url = url; }

    static DesktopBackend start(String externalUrl, String... args) {
        if (externalUrl != null && !externalUrl.isBlank()) return new DesktopBackend(null, externalUrl);
        var arguments = new ArrayList<>(Arrays.asList(args));
        arguments.add("--server.port=0");
        arguments.add("--server.address=127.0.0.1");
        arguments.add("--spring.main.headless=false");
        var context = new SpringApplicationBuilder(AtlasApplication.class).headless(false).run(arguments.toArray(String[]::new));
        int port = ((WebServerApplicationContext) context).getWebServer().getPort();
        return new DesktopBackend(context, "http://127.0.0.1:" + port + "/");
    }

    String url() { return url; }
    @Override public void close() { if (context != null) context.close(); }
}
