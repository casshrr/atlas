package com.example.atlas.expense;

import java.util.Currency;
import java.util.List;
import java.util.Locale;

/** Shared catalogue: the desktop and API agree on values, not just their spelling. */
public final class ExpenseOptions {
    public static final List<String> CATEGORIES = List.of("Food", "Transport", "Shopping", "Housing",
            "Bills", "Health", "Entertainment", "Education", "Travel", "Gifts", "Other");
    public static final List<String> CURRENCIES = List.of("KZT", "GBP", "USD", "EUR", "CHF", "CAD",
            "AUD", "NZD", "CNY", "INR", "AED", "TRY", "SGD", "HKD", "SEK", "NOK", "DKK", "PLN", "CZK", "ZAR");

    private ExpenseOptions() {}

    public static String category(String input) {
        if (input == null) return null;
        return CATEGORIES.stream().filter(c -> c.equalsIgnoreCase(input.strip())).findFirst().orElse(input.strip());
    }

    public static String currencyLabel(String code) {
        return code + " · " + Currency.getInstance(code).getDisplayName(Locale.ENGLISH);
    }
}
