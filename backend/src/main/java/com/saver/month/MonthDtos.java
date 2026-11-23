package com.saver.month;

import com.saver.expense.ExpenseDtos.OccurrenceResponse;
import com.saver.income.IncomeKind;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

public final class MonthDtos {

    private MonthDtos() {}

    /**
     * Totales de un mes por moneda (sin convertir: la cotización llega en la etapa 3).
     * {@code balance} = ingresos − gastos del mes. {@code overdue} = impagos de meses anteriores.
     */
    public record CurrencyTotals(String currency, BigDecimal income, BigDecimal expenses, BigDecimal paid,
                                 BigDecimal pending, BigDecimal overdue, BigDecimal balance) {}

    public record CategoryTotal(UUID categoryId, String currency, BigDecimal amount) {}

    /** {@code received}: lo cobrado en el mes (suma de los cobros registrados). */
    public record MonthIncome(UUID incomeId, String description, IncomeKind kind, String currency,
                              BigDecimal amount, boolean recurring, BigDecimal received) {}

    /** {@code projected}: mes futuro calculado desde las plantillas (no editable). */
    public record MonthResponse(
            YearMonth month, YearMonth currentMonth, boolean projected,
            List<CurrencyTotals> totals, List<CategoryTotal> byCategory,
            List<MonthIncome> incomes, List<OccurrenceResponse> occurrences, List<OccurrenceResponse> overdue) {}
}
