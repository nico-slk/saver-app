package com.saver.income;

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
import java.util.UUID;

/** Monto de un ingreso vigente desde un mes (ej. aumento de sueldo). */
@Entity
@Table(name = "income_amounts")
public class IncomeAmount extends BaseEntity {


    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "income_id")
    private Income income;

    @Convert(converter = YearMonthConverter.class)
    @Column(name = "effective_month", nullable = false)
    private YearMonth effectiveMonth;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal amount;

    protected IncomeAmount() {}

    IncomeAmount(Income income, YearMonth effectiveMonth, BigDecimal amount) {
        this.income = income;
        this.effectiveMonth = effectiveMonth;
        this.amount = amount;
    }

    public YearMonth getEffectiveMonth() { return effectiveMonth; }
    public BigDecimal getAmount() { return amount; }
    void setAmount(BigDecimal amount) { this.amount = amount; }
}
