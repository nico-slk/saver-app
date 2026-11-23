package com.saver.payment;

import com.saver.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Pago (total o parcial) de una ocurrencia. {@code amount} en la moneda del gasto, {@code accountAmount}
 * en la de la cuenta. Un pago {@code assumed} se registró al cargar un gasto con meses pasados: no tiene cuenta.
 */
@Entity
@Table(name = "payments")
public class Payment extends BaseEntity {

    @Column(name = "user_id", nullable = false) private UUID userId;
    @Column(name = "occurrence_id", nullable = false) private UUID occurrenceId;
    @Column(name = "account_id") private UUID accountId;
    @Column(name = "paid_on", nullable = false) private LocalDate paidOn;
    @Column(nullable = false, precision = 15, scale = 2) private BigDecimal amount;
    @Column(name = "account_amount", precision = 15, scale = 2) private BigDecimal accountAmount;
    @Column(precision = 15, scale = 4) private BigDecimal rate;
    @Column(name = "rate_type", length = 12) private String rateType;
    @Column(length = 500) private String notes;
    @Column(nullable = false) private boolean assumed;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;

    protected Payment() {}

    public Payment(UUID userId, UUID occurrenceId, UUID accountId, LocalDate paidOn, BigDecimal amount,
                   BigDecimal accountAmount, BigDecimal rate, String rateType, String notes, boolean assumed, Instant now) {
        this.userId = userId;
        this.occurrenceId = occurrenceId;
        this.accountId = accountId;
        this.paidOn = paidOn;
        this.amount = amount;
        this.accountAmount = accountAmount;
        this.rate = rate;
        this.rateType = rateType;
        this.notes = notes;
        this.assumed = assumed;
        this.createdAt = now;
    }

    public UUID getUserId() { return userId; }
    public UUID getOccurrenceId() { return occurrenceId; }
    public UUID getAccountId() { return accountId; }
    public LocalDate getPaidOn() { return paidOn; }
    public BigDecimal getAmount() { return amount; }
    public BigDecimal getAccountAmount() { return accountAmount; }
    public BigDecimal getRate() { return rate; }
    public String getRateType() { return rateType; }
    public String getNotes() { return notes; }
    public boolean isAssumed() { return assumed; }
    public Instant getCreatedAt() { return createdAt; }
}
