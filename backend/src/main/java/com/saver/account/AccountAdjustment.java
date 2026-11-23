package com.saver.account;

import com.saver.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** Ajuste de saldo con signo (conciliación, intereses, comisiones). */
@Entity
@Table(name = "account_adjustments")
public class AccountAdjustment extends BaseEntity {

    @Column(name = "user_id", nullable = false) private UUID userId;
    @Column(name = "account_id", nullable = false) private UUID accountId;
    @Column(name = "adjusted_on", nullable = false) private LocalDate adjustedOn;
    @Column(nullable = false, precision = 15, scale = 2) private BigDecimal amount;
    @Column(length = 500) private String notes;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;

    protected AccountAdjustment() {}

    public AccountAdjustment(UUID userId, UUID accountId, LocalDate adjustedOn, BigDecimal amount, String notes, Instant now) {
        this.userId = userId;
        this.accountId = accountId;
        this.adjustedOn = adjustedOn;
        this.amount = amount;
        this.notes = notes;
        this.createdAt = now;
    }

    public UUID getUserId() { return userId; }
    public UUID getAccountId() { return accountId; }
}
