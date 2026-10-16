package com.example.atlas.capture;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;
import java.time.*;
import java.util.UUID;

@Entity
@Table(name = "captured_payments")
@Getter @Setter
public class CapturedPayment {
    @Id private UUID id;
    @Column(nullable = false, length = 64) private String payloadHash;
    @Column(nullable = false, length = 100) private String source;
    @Column(nullable = false) private Instant capturedAt;
    @Column(nullable = false, length = 100) private String timeZone;
    @Column(nullable = false, length = 4000) private String rawText;
    @Column(precision = 19, scale = 2) private BigDecimal amount;
    @Column(length = 3) private String currency;
    @Column(length = 200) private String merchant;
    @Column(length = 50) private String category;
    @Column(nullable = false) private LocalDate spentOn;
    @Column(nullable = false, length = 20) private String status;
    @Column(nullable = false, length = 1000) private String reason;
    private UUID expenseId;
    @Column(nullable = false) private Instant createdAt;
}
