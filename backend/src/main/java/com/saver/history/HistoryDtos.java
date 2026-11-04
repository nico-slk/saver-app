package com.saver.history;

import com.saver.expense.ExpenseKind;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

public final class HistoryDtos {

    private HistoryDtos() {}

    public record CategoryAmount(UUID categoryId, BigDecimal amount) {}

    /**
     * Un mes del historial. {@code expenses} respeta el filtro de categorías; {@code expensesTotal}
     * y {@code savings} (= ingresos − todos los gastos) no dependen del filtro.
     * {@code inflationPercent}: inflación de ese mes ({@code inflationOfficial} = dato oficial, si no estimada).
     * {@code realFactor}: multiplicador para expresar los montos del mes en moneda del último mes del
     * período (inflación acumulada de los meses siguientes); 1 para el último mes.
     */
    public record MonthPoint(YearMonth month, BigDecimal income, BigDecimal expenses, BigDecimal expensesTotal,
                             BigDecimal savings, List<CategoryAmount> byCategory,
                             BigDecimal inflationPercent, boolean inflationOfficial, BigDecimal realFactor) {}

    /** Variación entre dos valores; {@code percent} es null si el valor inicial es 0. */
    public record Change(BigDecimal from, BigDecimal to, BigDecimal diff, BigDecimal percent) {}

    public record Summary(
            Change savingsVsPreviousMonth,
            BigDecimal averageSavings,
            BigDecimal totalIncome,
            BigDecimal totalExpenses,
            BigDecimal totalSavings,
            Change expensesFirstToLast,
            /** Inflación acumulada entre el primer y el último mes del período. */
            BigDecimal inflationPercent,
            /** true si algún mes del período usa inflación estimada (sin dato oficial). */
            boolean inflationEstimated,
            /** Ahorro total expresado en moneda del último mes. */
            BigDecimal totalSavingsReal,
            /** Variación de gastos del primer al último mes descontando la inflación. */
            BigDecimal expensesRealPercent) {}

    public record HistoryResponse(YearMonth from, YearMonth to, String currency, List<UUID> categoryFilter,
                                  List<MonthPoint> months, Summary summary) {}

    /**
     * Un gasto comparado entre dos meses. {@code from}/{@code to} null = no existía en ese mes.
     * {@code realPercent}: variación descontada la inflación (positivo = subió más que la inflación).
     */
    public record ExpenseChange(UUID expenseId, String description, UUID categoryId, ExpenseKind kind,
                                BigDecimal from, BigDecimal to, BigDecimal diff, BigDecimal percent, String status,
                                BigDecimal realPercent) {}

    public record CategoryChange(UUID categoryId, Change change) {}

    public record CompareResponse(
            YearMonth from, YearMonth to, String currency,
            Change income, Change expenses, Change savings,
            BigDecimal averageSavings, int months,
            List<ExpenseChange> items, List<CategoryChange> categories,
            /** Inflación acumulada entre los dos meses (de la moneda elegida). */
            BigDecimal inflationPercent, boolean inflationEstimated,
            /** Ahorro del mes inicial expresado en moneda del mes final, contra el ahorro final. */
            Change savingsReal,
            /** Variación de los gastos totales descontada la inflación. */
            BigDecimal expensesRealPercent) {}
}
