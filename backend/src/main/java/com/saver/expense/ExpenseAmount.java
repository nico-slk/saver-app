package com.saver.expense;

import com.saver.common.BaseEntity;
import com.saver.common.YearMonthConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.YearMonth;

/** Monto de un gasto vigente desde un mes (ej. aumento del alquiler). */
@Entity
@Table(name = "expense_amounts")
public class ExpenseAmount extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "expense_id")
    private Expense expense;

    @Convert(converter = YearMonthConverter.class)
    @Column(name = "effective_month", nullable = false)
    private YearMonth effectiveMonth;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal amount;

    protected ExpenseAmount() {}

    ExpenseAmount(Expense expense, YearMonth effectiveMonth, BigDecimal amount) {
        this.expense = expense;
        this.effectiveMonth = effectiveMonth;
        this.amount = amount;
    }

    public YearMonth getEffectiveMonth() { return effectiveMonth; }
    public BigDecimal getAmount() { return amount; }
    void setAmount(BigDecimal amount) { this.amount = amount; }
}
