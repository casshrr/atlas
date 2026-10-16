package com.example.atlas.desktop;

import org.junit.jupiter.api.Test;
import java.time.LocalDate;
import static org.junit.jupiter.api.Assertions.*;

class DateRangePresetTests {
    @Test void previousMonthHandlesLeapYearsAndYearBoundaries() {
        var leap = DateRangePreset.LAST_MONTH.range(LocalDate.of(2024, 3, 15));
        assertEquals(LocalDate.of(2024, 2, 1), leap.from());
        assertEquals(LocalDate.of(2024, 2, 29), leap.to());
        var january = DateRangePreset.LAST_MONTH.range(LocalDate.of(2026, 1, 1));
        assertEquals(LocalDate.of(2025, 12, 1), january.from());
        assertEquals(LocalDate.of(2025, 12, 31), january.to());
    }
    @Test void sevenDaysIsInclusiveAndAllTimeHasOpenBounds() {
        var today = LocalDate.of(2026, 10, 15);
        assertEquals(today.minusDays(6), DateRangePreset.LAST_7_DAYS.range(today).from());
        assertEquals(today, DateRangePreset.LAST_7_DAYS.range(today).to());
        assertNull(DateRangePreset.ALL_TIME.range(today).from());
        assertNull(DateRangePreset.ALL_TIME.range(today).to());
        assertEquals(LocalDate.of(2026, 10, 1), DateRangePreset.THIS_MONTH.range(today).from());
    }
}
