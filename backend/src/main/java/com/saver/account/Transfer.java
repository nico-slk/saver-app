package com.saver.account;

import com.saver.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** Transferencia entre cuentas propias; si las monedas difieren es una compra/venta de dólares. */
@Entity
@Table(name = "transfers")
public class Transfer extends BaseEntity {

    @Column(name = "user_id", nullable = false) private UUID userId;
    @Column(name = "from_account_id", nullable = false) private UUID fromAccountId;
    @Column(name = "to_account_id", nullable = false) private UUID toAccountId;
    @Column(name = "transfer_on", nullable = false) private LocalDate transferOn;
    @Column(name = "from_amount", nullable = false, precision = 15, scale = 2) private BigDecimal fromAmount;
    @Column(name = "to_amount", nullable = false, precision = 15, scale = 2) private BigDecimal toAmount;
    @Column(precision = 15, scale = 4) private BigDecimal rate;
    @Column(name = "rate_type", length = 12) private String rateType;
    @Column(length = 500) private String notes;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;

    protected Transfer() {}

    public Transfer(UUID userId, UUID fromAccountId, UUID toAccountId, LocalDate transferOn, BigDecimal fromAmount,
                    BigDecimal toAmount, BigDecimal rate, String rateType, String notes, Instant now) {
        this.userId = userId;
        this.fromAccountId = fromAccountId;
        this.toAccountId = toAccountId;
        this.transferOn = transferOn;
        this.fromAmount = fromAmount;
        this.toAmount = toAmount;
        this.rate = rate;
        this.rateType = rateType;
        this.notes = notes;
        this.createdAt = now;
    }

    public UUID getUserId() { return userId; }
    public UUID getFromAccountId() { return fromAccountId; }
    public UUID getToAccountId() { return toAccountId; }
    public LocalDate getTransferOn() { return transferOn; }
    public BigDecimal getFromAmount() { return fromAmount; }
    public BigDecimal getToAmount() { return toAmount; }
    public BigDecimal getRate() { return rate; }
    public String getRateType() { return rateType; }
    public String getNotes() { return notes; }
}
