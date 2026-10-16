package com.example.atlas.fx;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import java.time.LocalDate;

/** Persist successful observations, so a later refresh uses the same historical rate. */
@Component
public class HistoricalRateCache {
    private final JdbcTemplate jdbc;
    private final HistoricalRateProvider provider;

    public HistoricalRateCache(JdbcTemplate jdbc, HistoricalRateProvider provider) { this.jdbc = jdbc; this.provider = provider; }

    public HistoricalRateProvider.Rate find(String from, String to, LocalDate requested, boolean allowNetwork) throws Exception {
        var cached = jdbc.query("SELECT rate, rate_date FROM exchange_rates WHERE from_currency=? AND to_currency=? AND purchase_date=?",
                (rs, index) -> new HistoricalRateProvider.Rate(rs.getBigDecimal("rate"), rs.getDate("rate_date").toLocalDate()), from, to, requested);
        if (!cached.isEmpty()) return cached.get(0);
        if (!allowNetwork) throw new IllegalStateException("Rate lookup budget exceeded");
        var rate = provider.fetch(from, to, requested);
        jdbc.update("INSERT INTO exchange_rates(from_currency,to_currency,purchase_date,rate_date,rate,source) VALUES (?,?,?,?,?,?) ON CONFLICT DO NOTHING",
                from, to, requested, rate.date(), rate.value(), "Frankfurter");
        // Another request may have inserted first: always return the winning stored snapshot.
        return find(from, to, requested, false);
    }
}
