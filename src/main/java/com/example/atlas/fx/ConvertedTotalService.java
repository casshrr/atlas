package com.example.atlas.fx;

import com.example.atlas.expense.ExpenseOptions;
import com.example.atlas.expense.ExpenseService;
import org.springframework.stereotype.Service;
import java.math.*;
import java.time.*;
import java.util.*;

@Service
public class ConvertedTotalService {
    private final ExpenseService expenses;
    private final HistoricalRateCache rates;

    public ConvertedTotalService(ExpenseService expenses, HistoricalRateCache rates) { this.expenses = expenses; this.rates = rates; }

    public ConvertedTotal calculate(String currency, String category, LocalDate from, LocalDate to) {
        currency = currency.strip().toUpperCase(Locale.ROOT);
        if (!ExpenseOptions.CURRENCIES.contains(currency)) throw new IllegalArgumentException("Choose a supported display currency");
        if (from != null && to != null && from.isAfter(to)) throw new IllegalArgumentException("From date must not be after to date");
        var inputs = expenses.conversionInputs(category, from, to);
        var missing = new LinkedHashSet<String>();
        var observed = new LinkedHashSet<RateUsed>();
        var fetched = new HashMap<String, HistoricalRateProvider.Rate>();
        BigDecimal total = BigDecimal.ZERO;
        long deadline = System.nanoTime() + Duration.ofSeconds(8).toNanos();
        for (var input : inputs) {
            if (input.amount().signum() == 0) continue;
            if (input.currency().equals(currency)) { total = total.add(input.amount()); continue; }
            String key = input.currency() + "/" + currency + " on " + input.date();
            if (missing.contains(key)) continue;
            try {
                var rate = fetched.get(key);
                if (rate == null) {
                    rate = rates.find(input.currency(), currency, input.date(), System.nanoTime() < deadline);
                    fetched.put(key, rate);
                }
                total = total.add(input.amount().multiply(rate.value()).setScale(2, RoundingMode.HALF_EVEN));
                observed.add(new RateUsed(input.currency(), currency, input.date(), rate.date(), rate.value()));
            } catch (Exception ex) {
                if (ex instanceof InterruptedException) Thread.currentThread().interrupt();
                missing.add(key);
            }
        }
        boolean complete = missing.isEmpty();
        return new ConvertedTotal(currency, complete ? total.setScale(2, RoundingMode.HALF_EVEN) : null,
                complete, List.copyOf(missing), List.copyOf(observed), "Frankfurter · historical reference rates");
    }

    public record RateUsed(String from, String to, LocalDate purchaseDate, LocalDate rateDate, BigDecimal rate) {}
    public record ConvertedTotal(String currency, BigDecimal amount, boolean complete, List<String> missingRates,
                                 List<RateUsed> ratesUsed, String source) {}
}
