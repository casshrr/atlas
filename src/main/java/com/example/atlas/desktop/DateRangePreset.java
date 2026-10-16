package com.example.atlas.desktop;

import java.time.LocalDate;

enum DateRangePreset {
    ALL_TIME("All time"), TODAY("Today"), YESTERDAY("Yesterday"), LAST_7_DAYS("Last 7 days"),
    THIS_MONTH("This month"), LAST_MONTH("Last month"), THIS_YEAR("This year"), CUSTOM("Custom range");

    private final String label;
    DateRangePreset(String label) { this.label = label; }
    @Override public String toString() { return label; }

    Range range(LocalDate today) {
        return switch (this) {
            case ALL_TIME, CUSTOM -> new Range(null, null);
            case TODAY -> new Range(today, today);
            case YESTERDAY -> new Range(today.minusDays(1), today.minusDays(1));
            case LAST_7_DAYS -> new Range(today.minusDays(6), today);
            case THIS_MONTH -> new Range(today.withDayOfMonth(1), today);
            case LAST_MONTH -> new Range(today.minusMonths(1).withDayOfMonth(1), today.withDayOfMonth(1).minusDays(1));
            case THIS_YEAR -> new Range(today.withDayOfYear(1), today);
        };
    }

    record Range(LocalDate from, LocalDate to) {}
}
