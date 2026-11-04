package com.saver.projection;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

public final class ProjectionDtos {

    private ProjectionDtos() {}

    /**
     * Algo que pasa en un mes: termina una cuota (INSTALLMENTS_END), aumenta un gasto (INCREASE),
     * aumenta un ingreso (INCOME_INCREASE), termina un ingreso (INCOME_ENDS).
     */
    public record ProjectionEvent(String type, UUID refId, String description, BigDecimal amount) {}

    /**
     * Un mes proyectado. {@code cumulative} = saldo inicial + ahorros acumulados (nominal).
     * {@code cumulativeReal} = lo mismo expresado en pesos/dólares de hoy (descontando la inflación).
     * {@code inflationPercent}: inflación mensual usada para ese mes ({@code inflationOfficial} = dato oficial).
     */
    public record ProjectionMonth(
            YearMonth month, BigDecimal income, BigDecimal expenses,
            BigDecimal installments, BigDecimal recurring, BigDecimal oneTime,
            BigDecimal balance, BigDecimal cumulative, BigDecimal cumulativeReal,
            BigDecimal inflationPercent, boolean inflationOfficial,
            List<ProjectionEvent> events) {}

    /**
     * Inflación usada. {@code source}: SCREEN (elegida en la pantalla), MANUAL (del perfil), ESTIMATED
     * (promedio de los últimos meses oficiales) o NONE (sin datos, 0 %).
     * {@code accumulatedPercent}: inflación acumulada en todo el horizonte.
     */
    public record ProjectionInflation(
            BigDecimal monthlyPercent, String source,
            BigDecimal estimatedPercent, BigDecimal manualPercent, YearMonth lastOfficialMonth,
            BigDecimal accumulatedPercent) {}

    public record ProjectionSummary(
            BigDecimal finalBalance, BigDecimal finalBalanceReal,
            BigDecimal averageMonthlySavings,
            /** Último mes con cuotas dentro del horizonte; null si no hay cuotas. */
            YearMonth installmentsEndMonth,
            /** Promedio de ahorro mensual una vez terminadas las cuotas; null si no terminan en el horizonte. */
            BigDecimal averageSavingsAfterInstallments,
            int negativeMonths) {}

    public record ProjectionResponse(
            YearMonth from, YearMonth to, int months, String currency,
            BigDecimal startBalance, ProjectionInflation inflation,
            List<ProjectionMonth> series, ProjectionSummary summary,
            /** Monedas con movimientos que no se incluyeron (se proyecta una sola moneda). */
            List<String> excludedCurrencies) {}
}
