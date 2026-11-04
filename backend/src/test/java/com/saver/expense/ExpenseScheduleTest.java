package com.saver.expense;

import static org.assertj.core.api.Assertions.assertThat;

import com.saver.inflation.InflationCurve;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ExpenseScheduleTest {

    private static final UUID USER = UUID.randomUUID();
    private static final UUID CATEGORY = UUID.randomUUID();

    private static Expense installments(int n) {
        return new Expense(USER, CATEGORY, "Tarjeta", ExpenseKind.INSTALLMENTS, "ARS",
                YearMonth.of(2026, 11), n, null, new BigDecimal("15000.00"));
    }

    private static Expense rent() {
        Expense e = new Expense(USER, CATEGORY, "Alquiler", ExpenseKind.RECURRING, "ARS",
                YearMonth.of(2026, 1), null, null, new BigDecimal("500000.00"));
        e.setAdjustment((short) 4, new BigDecimal("10"), false);
        return e;
    }

    @Test
    void installmentsSpanTheirMonthsAndAreNumbered() {
        Expense e = installments(3);
        assertThat(e.getEndMonth()).isEqualTo(YearMonth.of(2027, 1));
        assertThat(e.activeIn(YearMonth.of(2026, 10))).isFalse();
        assertThat(e.activeIn(YearMonth.of(2027, 1))).isTrue();
        assertThat(e.activeIn(YearMonth.of(2027, 2))).isFalse();
        assertThat(ExpenseSchedule.installmentNumber(e, YearMonth.of(2026, 11))).isEqualTo(1);
        assertThat(ExpenseSchedule.installmentNumber(e, YearMonth.of(2027, 1))).isEqualTo(3);
    }

    @Test
    void oneTimeExpenseOnlyAppliesToItsMonth() {
        Expense e = new Expense(USER, CATEGORY, "Auto", ExpenseKind.ONE_TIME, "ARS",
                YearMonth.of(2026, 10), null, null, BigDecimal.TEN);
        assertThat(e.getEndMonth()).isEqualTo(YearMonth.of(2026, 10));
        assertThat(ExpenseSchedule.installmentNumber(e, YearMonth.of(2026, 10))).isNull();
    }

    @Test
    void dueDateDefaultsToLastDayAndClampsShortMonths() {
        assertThat(ExpenseSchedule.dueDate(YearMonth.of(2026, 2), null)).isEqualTo(LocalDate.of(2026, 2, 28));
        assertThat(ExpenseSchedule.dueDate(YearMonth.of(2026, 2), (short) 31)).isEqualTo(LocalDate.of(2026, 2, 28));
        assertThat(ExpenseSchedule.dueDate(YearMonth.of(2026, 10), (short) 25)).isEqualTo(LocalDate.of(2026, 10, 25));
    }

    @Test
    void amountVersionsApplyFromTheirEffectiveMonth() {
        Expense e = rent();
        e.setAmountFrom(YearMonth.of(2026, 5), new BigDecimal("560000.00"));
        assertThat(e.amountFor(YearMonth.of(2026, 4))).isEqualByComparingTo("500000");
        assertThat(e.amountFor(YearMonth.of(2026, 5))).isEqualByComparingTo("560000");
        assertThat(e.amountFor(YearMonth.of(2026, 12))).isEqualByComparingTo("560000");
    }

    @Test
    void estimatedAmountCompoundsEveryAdjustmentPeriod() {
        Expense e = rent(); // 500.000 desde 2026-01, +10 % cada 4 meses
        assertThat(ExpenseSchedule.estimatedAmount(e, YearMonth.of(2026, 4), null)).isEqualByComparingTo("500000");
        assertThat(ExpenseSchedule.estimatedAmount(e, YearMonth.of(2026, 5), null)).isEqualByComparingTo("550000");
        assertThat(ExpenseSchedule.estimatedAmount(e, YearMonth.of(2026, 9), null)).isEqualByComparingTo("605000");
    }

    @Test
    void estimatedAmountRestartsFromTheLastRealIncrease() {
        Expense e = rent();
        e.setAmountFrom(YearMonth.of(2026, 5), new BigDecimal("580000.00"));
        assertThat(ExpenseSchedule.estimatedAmount(e, YearMonth.of(2026, 8), null)).isEqualByComparingTo("580000");
        assertThat(ExpenseSchedule.estimatedAmount(e, YearMonth.of(2026, 9), null)).isEqualByComparingTo("638000");
    }

    @Test
    void inflationLinkedIncreaseUsesMonthlyInflation() {
        Expense e = rent();
        e.setAdjustment((short) 4, null, true);
        // 2 % mensual durante 4 meses = 1,02^4
        assertThat(ExpenseSchedule.estimatedAmount(e, YearMonth.of(2026, 5),
                InflationCurve.none("ARS").withManual(new BigDecimal("0.02"))))
                .isEqualByComparingTo("541216.08");
    }

    @Test
    void nextAdjustmentIsComputedFromLastIncrease() {
        Expense e = rent();
        assertThat(ExpenseSchedule.nextAdjustment(e, YearMonth.of(2026, 3))).isEqualTo(YearMonth.of(2026, 5));
        assertThat(ExpenseSchedule.nextAdjustment(e, YearMonth.of(2026, 5))).isEqualTo(YearMonth.of(2026, 9));
        e.setAmountFrom(YearMonth.of(2026, 6), new BigDecimal("600000"));
        assertThat(ExpenseSchedule.nextAdjustment(e, YearMonth.of(2026, 7))).isEqualTo(YearMonth.of(2026, 10));
        assertThat(ExpenseSchedule.nextAdjustment(installments(3), YearMonth.of(2026, 11))).isNull();
    }
}
