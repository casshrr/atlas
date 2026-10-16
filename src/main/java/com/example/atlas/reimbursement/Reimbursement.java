package com.example.atlas.reimbursement;

import jakarta.persistence.*;
import lombok.Getter;
import java.math.BigDecimal;
import java.time.*;
import java.util.UUID;

@Entity
@Table(name = "reimbursements")
@Getter
public class Reimbursement {
    @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
    @Column(name = "expense_id", nullable = false) private UUID expenseId;
    @Column(nullable = false, precision = 19, scale = 2) private BigDecimal amount;
    @Column(nullable = false, length = 3) private String currency;
    @Column(name = "received_on", nullable = false) private LocalDate receivedOn;
    @Column(length = 500) private String note;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;

    protected Reimbursement() {}
    public Reimbursement(UUID expenseId, BigDecimal amount, String currency, LocalDate receivedOn, String note) {
        this.expenseId = expenseId; this.amount = amount; this.currency = currency;
        this.receivedOn = receivedOn; this.note = note; this.createdAt = Instant.now();
    }
}
