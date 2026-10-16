package com.example.atlas.capture;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** One immutable file per notification. Retry the same eventId, never generate a new ID on retry. */
public record CaptureRequest(
        @NotNull UUID eventId,
        @NotBlank @Size(max = 100) String source,
        @NotNull @PastOrPresent Instant capturedAt,
        @NotBlank @Size(max = 100) String timeZone,
        @NotNull @Size(max = 4000) String text,
        @Positive @Digits(integer = 17, fraction = 2) BigDecimal amount,
        @Size(max = 3) String currency,
        @Size(max = 200) String merchant,
        @Size(max = 30) String kind) {}
