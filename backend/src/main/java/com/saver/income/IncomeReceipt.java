package com.saver.income;

import com.saver.common.BaseEntity;
import com.saver.common.YearMonthConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.UUID;

/** Cobro de un ingreso en un mes, acreditado en una cuenta. Puede haber varios (cobros parciales). */
@Entity
@Table(name = "income_receipts")
public class IncomeReceipt extends BaseEntity {

    @Column(name = "user_id", nullable = false) private UUID userId;
    @Column(name = "income_id", nullable = false) private UUID incomeId;
    @Convert(converter = YearMonthConverter.class)
    @Column(nullable = false) private YearMonth month;
    @Column(name = "account_id", nullable = false) private UUID accountId;
    @Column(name = "received_on", nullable = false) private LocalDate receivedOn;
    @Column(nullable = false, precision = 15, scale = 2) private BigDecimal amount;
    @Column(name = "account_amount", nullable = false, precision = 15, scale = 2) private BigDecimal accountAmount;
    @Column(precision = 15, scale = 4) private BigDecimal rate;
    @Column(name = "rate_type", length = 12) private String rateType;
    @Column(length = 500) private String notes;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;

    protected IncomeReceipt() {}

    public IncomeReceipt(UUID userId, UUID incomeId, YearMonth month, UUID accountId, LocalDate receivedOn,
                         BigDecimal amount, BigDecimal accountAmount, BigDecimal rate, String rateType, String notes,
                         Instant now) {
        this.userId = userId;
        this.incomeId = incomeId;
        this.month = month;
        this.accountId = accountId;
        this.receivedOn = receivedOn;
        this.amount = amount;
        this.accountAmount = accountAmount;
        this.rate = rate;
        this.rateType = rateType;
        this.notes = notes;
        this.createdAt = now;
    }

    public UUID getUserId() { return userId; }
    public UUID getIncomeId() { return incomeId; }
    public YearMonth getMonth() { return month; }
    public UUID getAccountId() { return accountId; }
    public LocalDate getReceivedOn() { return receivedOn; }
    public BigDecimal getAmount() { return amount; }
    public BigDecimal getAccountAmount() { return accountAmount; }
    public BigDecimal getRate() { return rate; }
    public String getRateType() { return rateType; }
    public String getNotes() { return notes; }
}
