package com.saver.expense;

import com.saver.common.BaseEntity;
import com.saver.common.Months;
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

/** Plantilla de gasto: genera una ocurrencia por mes dentro de [startMonth, endMonth]. */
@Entity
@Table(name = "expenses")
public class Expense extends BaseEntity {

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "category_id", nullable = false)
    private UUID categoryId;

    @Column(nullable = false, length = 120)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private ExpenseKind kind;

    @Column(nullable = false, length = 3)
    private String currency;

    @Convert(converter = YearMonthConverter.class)
    @Column(name = "start_month", nullable = false)
    private YearMonth startMonth;

    @Convert(converter = YearMonthConverter.class)
    @Column(name = "end_month")
    private YearMonth endMonth;

    @Column
    private Integer installments;

    /** Día de vencimiento; null = último día del mes. */
    @Column(name = "due_day")
    private Short dueDay;

    @Column(name = "variable_amount", nullable = false)
    private boolean variableAmount;

    @Column(name = "adjust_every_months")
    private Short adjustEveryMonths;

    @Column(name = "adjust_percent", precision = 7, scale = 4)
    private BigDecimal adjustPercent;

    @Column(name = "adjust_with_inflation", nullable = false)
    private boolean adjustWithInflation;

    @Column(length = 500)
    private String notes;

    /** Tarjeta con la que se paga (solo agrupa). */
    @Column(name = "card_id")
    private UUID cardId;

    @OneToMany(mappedBy = "expense", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("effectiveMonth ASC")
    private List<ExpenseAmount> amounts = new ArrayList<>();

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Expense() {}

    public Expense(UUID userId, UUID categoryId, String description, ExpenseKind kind, String currency,
                   YearMonth startMonth, Integer installments, YearMonth endMonth, BigDecimal amount) {
        this.userId = userId;
        this.categoryId = categoryId;
        this.description = description;
        this.kind = kind;
        this.currency = currency;
        this.startMonth = startMonth;
        this.installments = kind == ExpenseKind.INSTALLMENTS ? installments : null;
        this.endMonth = switch (kind) {
            case ONE_TIME -> startMonth;
            case INSTALLMENTS -> startMonth.plusMonths(installments - 1L);
            case RECURRING -> endMonth;
        };
        this.amounts.add(new ExpenseAmount(this, startMonth, amount));
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

    public Optional<ExpenseAmount> versionFor(YearMonth month) {
        return amounts.stream()
                .filter(a -> !a.getEffectiveMonth().isAfter(month))
                .max(Comparator.comparing(ExpenseAmount::getEffectiveMonth));
    }

    /** Monto cargado vigente en el mes (sin aumentos estimados). */
    public BigDecimal amountFor(YearMonth month) {
        return versionFor(month).map(ExpenseAmount::getAmount)
                .orElseGet(() -> amounts.getFirst().getAmount());
    }

    public void setAmountFrom(YearMonth from, BigDecimal amount) {
        amounts.stream().filter(a -> a.getEffectiveMonth().equals(from)).findFirst()
                .ifPresentOrElse(a -> a.setAmount(amount), () -> amounts.add(new ExpenseAmount(this, from, amount)));
    }

    public boolean hasAdjustmentRule() {
        return adjustmentRule() != null;
    }

    /** Regla de aumentos (solo gastos fijos); null si no tiene. */
    public com.saver.common.AdjustmentRule adjustmentRule() {
        return kind == ExpenseKind.RECURRING
                ? com.saver.common.AdjustmentRule.of(adjustEveryMonths, adjustPercent, adjustWithInflation) : null;
    }

    public UUID getUserId() { return userId; }
    public UUID getCategoryId() { return categoryId; }
    public void setCategoryId(UUID categoryId) { this.categoryId = categoryId; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public ExpenseKind getKind() { return kind; }
    public String getCurrency() { return currency; }
    public YearMonth getStartMonth() { return startMonth; }
    public YearMonth getEndMonth() { return endMonth; }
    public void setEndMonth(YearMonth endMonth) { this.endMonth = endMonth; }
    public Integer getInstallments() { return installments; }
    public Short getDueDay() { return dueDay; }
    public void setDueDay(Short dueDay) { this.dueDay = dueDay; }
    public boolean isVariableAmount() { return variableAmount; }
    public void setVariableAmount(boolean variableAmount) { this.variableAmount = variableAmount; }
    public Short getAdjustEveryMonths() { return adjustEveryMonths; }
    public BigDecimal getAdjustPercent() { return adjustPercent; }
    public boolean isAdjustWithInflation() { return adjustWithInflation; }
    public void setAdjustment(Short everyMonths, BigDecimal percent, boolean withInflation) {
        this.adjustEveryMonths = everyMonths;
        this.adjustPercent = percent;
        this.adjustWithInflation = withInflation;
    }
    public UUID getCardId() { return cardId; }
    public void setCardId(UUID cardId) { this.cardId = cardId; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
    public List<ExpenseAmount> getAmounts() { return amounts; }
}
