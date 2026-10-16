package com.example.atlas.desktop;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.nio.file.attribute.FileTime;
import java.net.InetSocketAddress;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.prefs.Preferences;
import static org.junit.jupiter.api.Assertions.*;

class FolderCaptureBridgeTests {
    @TempDir Path directory;
    @Test void readsOnlyCompletedJsonFilesAndDoesNotModifyTheFolder() throws Exception {
        var requests = new AtomicInteger();
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/captures", exchange -> {
            exchange.getRequestBody().readAllBytes(); requests.incrementAndGet();
            byte[] response = "{\"status\":\"REVIEW\"}".getBytes();
            exchange.sendResponseHeaders(200, response.length); exchange.getResponseBody().write(response); exchange.close();
        }); server.start();
        var preferences = Preferences.userRoot().node("atlas-tests/" + UUID.randomUUID());
        var file = directory.resolve("payment.json");
        String original = "{\"eventId\":\"" + UUID.randomUUID() + "\"}";
        Files.writeString(file, original); Files.setLastModifiedTime(file, FileTime.fromMillis(System.currentTimeMillis() - 5000));
        Files.writeString(directory.resolve("private.txt"), "not an import");
        Files.writeString(directory.resolve("incomplete.json"), "{");
        preferences.put("captureFolder", directory.toString());
        try (var bridge = new FolderCaptureBridge(new AtlasApiClient("http://127.0.0.1:" + server.getAddress().getPort()), () -> {}, preferences)) {
            bridge.scan(); bridge.scan(); assertEquals(1, requests.get()); assertEquals(original, Files.readString(file));
            try (var files = Files.list(directory)) { assertEquals(3, files.count()); }
            bridge.disconnect(); bridge.scan(); assertEquals(1, requests.get());
        } finally { server.stop(0); preferences.removeNode(); }
    }

    @Test void malformedOversizedAndSymlinkFilesCannotPreventAValidDelivery() throws Exception {
        var requests = new AtomicInteger();
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/captures", exchange -> { exchange.getRequestBody().readAllBytes(); requests.incrementAndGet(); exchange.sendResponseHeaders(204, -1); exchange.close(); }); server.start();
        var preferences = Preferences.userRoot().node("atlas-tests/" + UUID.randomUUID());
        Files.writeString(directory.resolve("good.json"), "{}");
        Files.writeString(directory.resolve("broken.json"), "not json");
        Files.writeString(directory.resolve("large.json"), " ".repeat(65537));
        Files.createSymbolicLink(directory.resolve("linked.json"), directory.resolve("good.json"));
        try (var files = Files.list(directory)) { files.forEach(path -> { try { if (!Files.isSymbolicLink(path)) Files.setLastModifiedTime(path, FileTime.fromMillis(System.currentTimeMillis() - 5000)); } catch (Exception ex) { throw new RuntimeException(ex); } }); }
        preferences.put("captureFolder", directory.toString());
        try (var bridge = new FolderCaptureBridge(new AtlasApiClient("http://127.0.0.1:" + server.getAddress().getPort()), () -> {}, preferences)) {
            bridge.scan(); assertEquals(1, requests.get()); assertTrue(bridge.message().contains("could not be imported"));
            bridge.scan(); assertEquals(1, requests.get());
        } finally { server.stop(0); preferences.removeNode(); }
    }
}
