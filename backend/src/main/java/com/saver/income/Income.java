package com.saver.income;

import com.saver.common.Months;
import com.saver.common.AdjustmentRule;
import com.saver.common.BaseEntity;
import com.saver.inflation.InflationCurve;
import com.saver.common.YearMonthConverter;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Ingreso con vigencia [startMonth, endMonth]. endMonth null = indefinido.
 * Un extra puntual tiene startMonth == endMonth, por eso no se proyecta a meses siguientes.
 */
@Entity
@Table(name = "incomes")
public class Income extends BaseEntity {


    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(nullable = false, length = 120)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private IncomeKind kind;

    @Column(nullable = false, length = 3)
    private String currency;

    @Convert(converter = YearMonthConverter.class)
    @Column(name = "start_month", nullable = false)
    private YearMonth startMonth;

    @Convert(converter = YearMonthConverter.class)
    @Column(name = "end_month")
    private YearMonth endMonth;

    @Column(name = "adjust_every_months")
    private Short adjustEveryMonths;

    @Column(name = "adjust_percent", precision = 7, scale = 4)
    private BigDecimal adjustPercent;

    @Column(name = "adjust_with_inflation", nullable = false)
    private boolean adjustWithInflation;

    @OneToMany(mappedBy = "income", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("effectiveMonth ASC")
    private List<IncomeAmount> amounts = new ArrayList<>();

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Income() {}

    public Income(UUID userId, String description, IncomeKind kind, String currency,
                  YearMonth startMonth, YearMonth endMonth, BigDecimal amount) {
        this.userId = userId;
        this.description = description;
        this.kind = kind;
        this.currency = currency;
        this.startMonth = startMonth;
        this.endMonth = endMonth;
        this.amounts.add(new IncomeAmount(this, startMonth, amount));
    }

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public boolean activeIn(YearMonth month) {
        return Months.inRange(month, startMonth, endMonth);
    }

    /** Monto vigente en el mes: la última versión con effectiveMonth <= mes. */
    public Optional<BigDecimal> amountFor(YearMonth month) {
        return amounts.stream()
                .filter(a -> !a.getEffectiveMonth().isAfter(month))
                .max(Comparator.comparing(IncomeAmount::getEffectiveMonth))
                .map(IncomeAmount::getAmount);
    }

    /** Agrega o reemplaza el monto vigente desde {@code from}. */
    public void setAmountFrom(YearMonth from, BigDecimal amount) {
        amounts.stream().filter(a -> a.getEffectiveMonth().equals(from)).findFirst()
                .ifPresentOrElse(a -> a.setAmount(amount), () -> amounts.add(new IncomeAmount(this, from, amount)));
    }

    /** Un ingreso de un solo mes no se repite y no puede tener regla de aumentos. */
    public boolean isRecurring() {
        return !startMonth.equals(endMonth);
    }

    /** Regla de aumentos estimados; null si no tiene. */
    public AdjustmentRule adjustmentRule() {
        return isRecurring() ? AdjustmentRule.of(adjustEveryMonths, adjustPercent, adjustWithInflation) : null;
    }

    /**
     * Monto estimado para un mes futuro: el último monto cargado más los aumentos de la regla.
     * {@code curve}: inflación de la moneda del ingreso (null = 0 %).
     */
    public Optional<BigDecimal> estimatedAmount(YearMonth month, InflationCurve curve) {
        Optional<IncomeAmount> version = amounts.stream()
                .filter(a -> !a.getEffectiveMonth().isAfter(month))
                .max(Comparator.comparing(IncomeAmount::getEffectiveMonth));
        AdjustmentRule rule = adjustmentRule();
        return version.map(v -> rule == null ? v.getAmount()
                : rule.estimate(v.getAmount(), v.getEffectiveMonth(), month, curve));
    }

    /** Próximo mes de aumento estimado posterior a {@code current}; null si no hay regla o ya terminó. */
    public YearMonth nextAdjustment(YearMonth current) {
        AdjustmentRule rule = adjustmentRule();
        if (rule == null) return null;
        YearMonth last = amounts.stream().filter(a -> !a.getEffectiveMonth().isAfter(current))
                .map(IncomeAmount::getEffectiveMonth).max(Comparator.naturalOrder()).orElse(startMonth);
        YearMonth next = rule.next(last, current);
        return endMonth != null && next.isAfter(endMonth) ? null : next;
    }

    public Short getAdjustEveryMonths() { return adjustEveryMonths; }
    public BigDecimal getAdjustPercent() { return adjustPercent; }
    public boolean isAdjustWithInflation() { return adjustWithInflation; }
    public void setAdjustment(Short everyMonths, BigDecimal percent, boolean withInflation) {
        this.adjustEveryMonths = everyMonths;
        this.adjustPercent = percent;
        this.adjustWithInflation = withInflation;
    }

    public UUID getUserId() { return userId; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public IncomeKind getKind() { return kind; }
    public String getCurrency() { return currency; }
    public YearMonth getStartMonth() { return startMonth; }
    public YearMonth getEndMonth() { return endMonth; }
    public void setEndMonth(YearMonth endMonth) { this.endMonth = endMonth; }
    public List<IncomeAmount> getAmounts() { return amounts; }
}
