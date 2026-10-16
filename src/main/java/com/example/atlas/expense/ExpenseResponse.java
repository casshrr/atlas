package com.example.atlas.expense;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record ExpenseResponse(UUID id, BigDecimal amount, String currency, String category,
                              String description, LocalDate spentOn, Instant createdAt,
                              BigDecimal reimbursedAmount, BigDecimal netAmount) {
    public static ExpenseResponse from(Expense e, BigDecimal reimbursed) {
        return new ExpenseResponse(e.getId(), e.getAmount(), e.getCurrency(), e.getCategory(),
                e.getDescription(), e.getSpentOn(), e.getCreatedAt(), reimbursed, e.getAmount().subtract(reimbursed));
    }
}
