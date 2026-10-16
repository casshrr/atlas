package com.example.atlas.expense;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Locale;

/** Shared create/replace contract; server-owned fields deliberately excluded. */
public record ExpenseRequest(
        @NotNull @Positive @Digits(integer = 17, fraction = 2) BigDecimal amount,
        @NotBlank @ExpenseChoice(ExpenseChoice.Kind.CURRENCY) String currency,
        @NotBlank @ExpenseChoice(ExpenseChoice.Kind.CATEGORY) String category,
        @Size(max = 500) String description,
        @NotNull @PastOrPresent LocalDate spentOn) {
    public ExpenseRequest {
        currency = currency == null ? null : currency.strip().toUpperCase(Locale.ROOT);
        category = ExpenseOptions.category(category);
        description = description == null ? null : description.strip();
    }
}
