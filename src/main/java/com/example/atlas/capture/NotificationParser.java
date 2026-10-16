package com.example.atlas.capture;

import com.example.atlas.expense.ExpenseOptions;
import org.springframework.stereotype.Component;
import java.math.BigDecimal;
import java.util.*;
import java.util.regex.Pattern;

/** Deliberately small grammar: unfamiliar formats go to review instead of guessing. */
@Component
public class NotificationParser {
    private static final String MONEY = "(GBP|KZT|USD|EUR|CHF|CAD|AUD|NZD|CNY|INR|AED|TRY|SGD|HKD|SEK|NOK|DKK|PLN|CZK|ZAR|£|€|₸|\\$)";
    private static final String NUMBER = "([0-9]+(?:[.,\\u00a0 ][0-9]+)*)";
    private static final Pattern BEFORE = Pattern.compile(MONEY + "\\s*" + NUMBER, Pattern.CASE_INSENSITIVE);
    private static final Pattern AFTER = Pattern.compile(NUMBER + "\\s*" + MONEY, Pattern.CASE_INSENSITIVE);
    private static final Pattern PURCHASE = Pattern.compile("(?iu)\\b(paid|purchase|payment|spent)\\b");
    private static final Pattern UNSAFE = Pattern.compile("(?iu)\\b(refund|reversal|reversed|declined|failed|pending|transfer|received|cashback|balance|fee|cancelled|canceled|authorization|authorisation|hold)\\b");

    public Parsed parse(CaptureRequest request) {
        var reasons = new ArrayList<String>();
        String text = request.text();
        var values = new ArrayList<Money>();
        var before = BEFORE.matcher(text);
        while (before.find()) values.add(money(before.group(2), before.group(1)));
        // Avoid counting the same expression twice; multiple money values are always uncertain.
        var after = AFTER.matcher(text);
        while (after.find()) {
            boolean overlaps = false;
            before.reset();
            while (before.find()) if (before.start() < after.end() && after.start() < before.end()) overlaps = true;
            if (!overlaps) values.add(money(after.group(1), after.group(2)));
        }
        BigDecimal amount = request.amount();
        String currency = clean(request.currency());
        currency = currency == null ? null : currency.toUpperCase(Locale.ROOT);
        if (values.size() == 1) {
            var value = values.get(0);
            if (amount == null) amount = value.amount();
            else if (value.amount() == null || amount.compareTo(value.amount()) != 0) reasons.add("Amount conflicts with notification");
            if (currency == null) currency = value.currency();
            else if (value.currency() == null || !currency.equals(value.currency())) reasons.add("Currency conflicts with notification");
        } else if (values.size() > 1) reasons.add("Several amounts appear in this notification");
        if (amount == null || amount.signum() <= 0) reasons.add("Check the amount and number format");
        if (!ExpenseOptions.CURRENCIES.contains(currency == null ? "" : currency)) { currency = null; reasons.add("Choose an unambiguous currency"); }
        String merchant = clean(request.merchant());
        if (merchant == null) {
            var template = Pattern.compile("(?iu)^(?:paid|spent)\\s+" + MONEY + "\\s*" + NUMBER + "\\s+at\\s+([^\\r\\n]{1,200})$").matcher(text.strip());
            if (template.matches()) merchant = clean(template.group(3));
        }
        // Merchant must be explicitly supplied by a verified Shortcut mapping, or confirmed in review.
        if (merchant == null) reasons.add("Confirm the merchant");
        String kind = clean(request.kind());
        boolean purchase = "purchase".equalsIgnoreCase(kind) || (kind == null && PURCHASE.matcher(text).find());
        if (!purchase || UNSAFE.matcher(text).find()) reasons.add("Confirm this is a completed purchase, not income, a refund or a declined payment");
        if (Pattern.compile("[-−]\\s*(?:" + MONEY + "\\s*)?[0-9]", Pattern.CASE_INSENSITIVE).matcher(text).find()) reasons.add("Confirm the meaning of a signed amount");
        return new Parsed(amount, currency, merchant, reasons);
    }

    private Money money(String number, String symbol) {
        String currency = switch (symbol) { case "£" -> "GBP"; case "€" -> "EUR"; case "₸" -> "KZT"; case "$" -> null; default -> symbol.toUpperCase(Locale.ROOT); };
        // Only plain integers and a single two-digit decimal separator. Grouping is locale-dependent.
        if (!number.matches("[0-9]+(?:[.,][0-9]{2})?")) return new Money(null, currency);
        var amount = new BigDecimal(number.replace(',', '.'));
        if (amount.precision() - amount.scale() > 17) amount = null;
        return new Money(amount, currency);
    }
    static String clean(String text) { return text == null || text.isBlank() ? null : text.strip(); }
    public record Parsed(BigDecimal amount, String currency, String merchant, List<String> reasons) {}
    private record Money(BigDecimal amount, String currency) {}
}
