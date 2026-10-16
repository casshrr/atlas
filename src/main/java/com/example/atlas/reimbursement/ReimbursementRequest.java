package com.example.atlas.reimbursement;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Locale;

/** Only money already received may be recorded. */
public record ReimbursementRequest(
        @NotNull @Positive @Digits(integer = 17, fraction = 2) BigDecimal amount,
        @NotBlank @Pattern(regexp = "[A-Z]{3}") String currency,
        @NotNull @PastOrPresent LocalDate receivedOn,
        @Size(max = 500) String note) {
    public ReimbursementRequest {
        currency = currency == null ? null : currency.strip().toUpperCase(Locale.ROOT);
        note = note == null ? null : note.strip();
    }
}
