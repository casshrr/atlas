package com.example.atlas.desktop;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import javax.swing.SwingUtilities;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.prefs.Preferences;

/** Read-only iCloud transport. Durable database IDs make rescanning the same files safe. */
final class FolderCaptureBridge implements AutoCloseable {
    private static final int MAX_FILE_BYTES = 65536;
    private static final int MAX_FILES_PER_SCAN = 100;
    private static final int SETTLE_SECONDS = 2;
    private static final long RETRY_DELAY_MILLIS = 60000;

    private final AtlasApiClient api;
    private final Runnable onImport;
    private final Preferences preferences;
    private final ScheduledExecutorService worker = Executors.newSingleThreadScheduledExecutor(task -> {
        var thread = new Thread(task, "atlas-phone-import");
        thread.setDaemon(true);
        return thread;
    });
    private final Map<Path, String> delivered = new ConcurrentHashMap<>();
    private final Map<Path, Long> retryAfter = new ConcurrentHashMap<>();
    private final JsonMapper json = JsonMapper.builder().build();
    private volatile String message = "Choose an iCloud folder to connect your iPhone.";

    FolderCaptureBridge(AtlasApiClient api, Runnable onImport) {
        this(api, onImport, Preferences.userNodeForPackage(FolderCaptureBridge.class));
    }

    FolderCaptureBridge(AtlasApiClient api, Runnable onImport, Preferences preferences) {
        this.api = api;
        this.onImport = onImport;
        this.preferences = preferences;
    }

    void start() {
        worker.scheduleWithFixedDelay(this::scan, 2, 10, TimeUnit.SECONDS);
    }

    String folder() {
        return preferences.get("captureFolder", "");
    }

    String message() {
        return message;
    }

    void choose(Path folder) {
        preferences.put("captureFolder", folder.toAbsolutePath().toString());
        clearDeliveryCache();
        message = "Connecting…";
        scanNow();
    }

    void disconnect() {
        preferences.remove("captureFolder");
        clearDeliveryCache();
        message = "Disconnected. Existing expenses and reviews are kept.";
    }

    void scanNow() {
        worker.execute(this::scan);
    }

    void scan() {
        String selectedFolder = folder();
        if (selectedFolder.isBlank()) {
            return;
        }

        int imported = 0;
        int failed = 0;
        try {
            Path directory = Path.of(selectedFolder);
            if (!Files.isDirectory(directory)) {
                message = "Folder unavailable. Check iCloud sync or choose it again.";
                return;
            }

            for (Path file : filesToScan(directory)) {
                if (!selectedFolder.equals(folder())) {
                    break;
                }
                try {
                    if (importFile(file)) {
                        imported++;
                    }
                } catch (Exception ex) {
                    failed++;
                    retryAfter.put(file, System.currentTimeMillis() + RETRY_DELAY_MILLIS);
                }
            }
            updateConnectionMessage(failed);
        } catch (Exception ex) {
            message = "Couldn’t read the folder. Check access and iCloud sync.";
        }
        if (imported > 0) {
            SwingUtilities.invokeLater(onImport);
        }
    }

    private List<Path> filesToScan(Path directory) throws java.io.IOException {
        try (var paths = Files.list(directory)) {
            // Skip delivered files before limiting the batch, so later files get a turn.
            return paths.filter(path -> path.getFileName().toString().endsWith(".json"))
                    .filter(path -> Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS))
                    .filter(path -> retryAfter.getOrDefault(path, 0L) <= System.currentTimeMillis())
                    .filter(path -> !stamp(path).equals(delivered.get(path)))
                    .limit(MAX_FILES_PER_SCAN)
                    .toList();
        }
    }

    private boolean importFile(Path file) throws Exception {
        String before = stamp(file);
        if (Files.getLastModifiedTime(file).toInstant().isAfter(Instant.now().minusSeconds(SETTLE_SECONDS))) {
            return false;
        }

        JsonNode body = readCapture(file);
        if (!before.equals(stamp(file))) {
            return false; // iCloud changed the file while we were reading it.
        }

        api.request("POST", "api/captures", body);
        delivered.put(file, before);
        retryAfter.remove(file);
        return true;
    }

    private JsonNode readCapture(Path file) throws java.io.IOException {
        byte[] bytes;
        try (var input = Files.newInputStream(file, LinkOption.NOFOLLOW_LINKS)) {
            bytes = input.readNBytes(MAX_FILE_BYTES + 1);
        }
        if (bytes.length > MAX_FILE_BYTES) {
            throw new IllegalArgumentException("File too large");
        }

        JsonNode body = json.readTree(bytes);
        if (!body.isObject()) {
            throw new IllegalArgumentException("Expected a JSON object");
        }
        return body;
    }

    private void updateConnectionMessage(int failed) {
        message = failed == 0 && retryAfter.isEmpty()
                ? "Connected · Checks every 10 seconds while Atlas is open."
                : "Some files could not be imported. Check their format and iCloud download status; Atlas will retry.";
    }

    private void clearDeliveryCache() {
        delivered.clear();
        retryAfter.clear();
    }

    private String stamp(Path file) {
        try {
            return Files.getLastModifiedTime(file).toMillis() + ":" + Files.size(file);
        } catch (Exception ex) {
            return "unavailable";
        }
    }

    @Override
    public void close() {
        worker.shutdownNow();
    }
}
