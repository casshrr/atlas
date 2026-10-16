package com.example.atlas.expense;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "expenses")
public class Expense {

    @Getter
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Getter
    @Setter
    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Getter
    @Setter
    @Column(nullable = false, length = 3)
    private String currency;

    @Getter
    @Setter
    @Column(nullable = false, length = 50)
    private String category;

    @Getter
    @Setter
    @Column(length = 500)
    private String description;

    @Getter
    @Setter
    @Column(name = "spent_on", nullable = false)
    private LocalDate spentOn;

    @Getter
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Expense() {
    }

    public Expense(
            BigDecimal amount,
            String currency,
            String category,
            String description,
            LocalDate spentOn
    ) {
        this.amount = amount;
        this.currency = currency;
        this.category = category;
        this.description = description;
        this.spentOn = spentOn;
        this.createdAt = Instant.now();
    }

}