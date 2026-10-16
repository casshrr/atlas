package com.example.atlas.insights;

import com.example.atlas.expense.*;
import org.springframework.stereotype.Service;
import java.math.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;

/** Deterministic insights, always separated by currency and based on purchase dates. */
@Service
public class SpendingInsightsService {
    private final ExpenseService expenses;
    public SpendingInsightsService(ExpenseService expenses) { this.expenses = expenses; }

    public Report report(LocalDate from, LocalDate to) {
        LocalDate end = to == null ? LocalDate.now() : to;
        LocalDate start = from == null ? end.withDayOfMonth(1) : from;
        long days = ChronoUnit.DAYS.between(start, end) + 1;
        if (days < 1 || days > 366) throw new IllegalArgumentException("Choose an insights interval between 1 and 366 days");
        if (end.isAfter(LocalDate.now())) throw new IllegalArgumentException("Insights cannot include future dates");
        LocalDate previousStart = start.minusDays(days);
        LocalDate previousEnd = start.minusDays(1);
        var rows = expenses.reportingRows(null, previousStart, end);
        var currencies = new TreeSet<String>(); rows.forEach(e -> currencies.add(e.currency()));
        var result = new ArrayList<CurrencyInsight>();
        for (String currency : currencies) {
            var current = rows.stream().filter(e -> e.currency().equals(currency) && !e.spentOn().isBefore(start)).toList();
            var previous = rows.stream().filter(e -> e.currency().equals(currency) && e.spentOn().isBefore(start)).toList();
            BigDecimal gross = current.stream().map(ExpenseResponse::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal reimbursed = current.stream().map(ExpenseResponse::reimbursedAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal net = gross.subtract(reimbursed);
            BigDecimal prior = previous.stream().map(ExpenseResponse::netAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal change = net.subtract(prior);
            BigDecimal changePercent = prior.signum() == 0 ? null : change.multiply(BigDecimal.valueOf(100)).divide(prior, 1, RoundingMode.HALF_EVEN);
            var categoryMap = new TreeMap<String, BigDecimal>();
            current.forEach(e -> categoryMap.merge(e.category(), e.netAmount(), BigDecimal::add));
            var categories = categoryMap.entrySet().stream().map(e -> new CategorySpending(e.getKey(), e.getValue(),
                    net.signum() == 0 ? BigDecimal.ZERO : e.getValue().multiply(BigDecimal.valueOf(100)).divide(net, 1, RoundingMode.HALF_EVEN)))
                    .sorted(Comparator.comparing(CategorySpending::amount).reversed().thenComparing(CategorySpending::category)).toList();
            var trend = new ArrayList<TrendPoint>();
            boolean daily = days <= 31;
            for (LocalDate cursor = start; !cursor.isAfter(end);) {
                LocalDate bucketStart = cursor;
                LocalDate bucketEnd = daily ? cursor : cursor.withDayOfMonth(cursor.lengthOfMonth());
                if (bucketEnd.isAfter(end)) bucketEnd = end;
                final LocalDate last = bucketEnd;
                var value = current.stream().filter(e -> !e.spentOn().isBefore(bucketStart) && !e.spentOn().isAfter(last))
                        .map(ExpenseResponse::netAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
                trend.add(new TrendPoint(cursor, bucketEnd, cursor.format(DateTimeFormatter.ofPattern(daily ? "d MMM" : "MMM yyyy")), value));
                cursor = bucketEnd.plusDays(1);
            }
            result.add(new CurrencyInsight(currency, gross, reimbursed, net, prior, change, changePercent,
                    net.divide(BigDecimal.valueOf(days), 2, RoundingMode.HALF_EVEN), categories, trend));
        }
        return new Report(start, end, previousStart, previousEnd, result,
                "Purchase-date basis: received reimbursements reduce the original purchase period. Each currency is reported separately.");
    }

    public record Report(LocalDate from, LocalDate to, LocalDate previousFrom, LocalDate previousTo,
                         List<CurrencyInsight> currencies, String basis) {}
    public record CurrencyInsight(String currency, BigDecimal grossAmount, BigDecimal reimbursedAmount,
            BigDecimal netAmount, BigDecimal previousNetAmount, BigDecimal changeAmount, BigDecimal changePercent,
            BigDecimal dailyAverage, List<CategorySpending> categories, List<TrendPoint> trend) {}
    public record CategorySpending(String category, BigDecimal amount, BigDecimal sharePercent) {}
    public record TrendPoint(LocalDate from, LocalDate to, String label, BigDecimal amount) {}
}
