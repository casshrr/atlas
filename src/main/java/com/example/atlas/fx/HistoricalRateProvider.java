package com.example.atlas.fx;

import java.math.BigDecimal;
import java.time.LocalDate;

public interface HistoricalRateProvider {
    Rate fetch(String from, String to, LocalDate purchaseDate) throws Exception;
    record Rate(BigDecimal value, LocalDate date) {}
}
