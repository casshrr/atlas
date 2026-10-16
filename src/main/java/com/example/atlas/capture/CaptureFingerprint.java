package com.example.atlas.capture;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HexFormat;

/** Identifies identical deliveries even if their event IDs differ. Not a bank transaction ID. */
final class CaptureFingerprint {
    private CaptureFingerprint() {}

    static String of(CaptureRequest request) {
        var fields = Arrays.asList(
                request.source(),
                request.capturedAt().toString(),
                request.timeZone(),
                request.text(),
                request.amount() == null ? null : request.amount().stripTrailingZeros().toPlainString(),
                request.currency(),
                request.merchant(),
                request.kind());

        // Keep this encoding stable: existing database fingerprints must still match retries.
        // Length prefixes distinguish a missing value from empty text or embedded delimiters.
        var canonical = new StringBuilder();
        for (String value : fields) {
            canonical.append(value == null ? -1 : value.length())
                    .append(':')
                    .append(value == null ? "" : value);
        }

        try {
            byte[] bytes = canonical.toString().getBytes(StandardCharsets.UTF_8);
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
