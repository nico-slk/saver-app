package com.saver.expense;

import com.saver.common.AdjustmentRule;
import com.saver.common.Months;
import com.saver.inflation.InflationCurve;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;

/** Cálculos puros sobre una plantilla de gasto (sin acceso a datos). */
public final class ExpenseSchedule {

    private ExpenseSchedule() {}

    /** Número de cuota (1..N) para gastos en cuotas; null para el resto. */
    public static Integer installmentNumber(Expense e, YearMonth month) {
        if (e.getKind() != ExpenseKind.INSTALLMENTS) return null;
        return (int) Months.between(e.getStartMonth(), month) + 1;
    }

    /** Vencimiento: el día configurado (ajustado a meses cortos) o el último día del mes. */
    public static LocalDate dueDate(YearMonth month, Short dueDay) {
        if (dueDay == null) return month.atEndOfMonth();
        return month.atDay(Math.min(dueDay, month.lengthOfMonth()));
    }

    /**
     * Monto estimado para un mes futuro: el último monto cargado más los aumentos estimados que
     * caen entre su vigencia y el mes (ver {@link AdjustmentRule}). {@code curve}: inflación de la
     * moneda del gasto (se usa si el gasto sigue a la inflación; null = 0 %).
     */
    public static BigDecimal estimatedAmount(Expense e, YearMonth month, InflationCurve curve) {
        ExpenseAmount version = e.versionFor(month).orElse(e.getAmounts().getFirst());
        AdjustmentRule rule = e.adjustmentRule();
        return rule == null ? version.getAmount()
                : rule.estimate(version.getAmount(), version.getEffectiveMonth(), month, curve);
    }

    /** Próximo mes de aumento estimado posterior a {@code current}; null si no hay regla. */
    public static YearMonth nextAdjustment(Expense e, YearMonth current) {
        AdjustmentRule rule = e.adjustmentRule();
        if (rule == null) return null;
        YearMonth last = e.versionFor(current).map(ExpenseAmount::getEffectiveMonth).orElse(e.getStartMonth());
        YearMonth next = rule.next(last, current);
        return e.getEndMonth() != null && next.isAfter(e.getEndMonth()) ? null : next;
    }
}
