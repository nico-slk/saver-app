package com.saver.inflation;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.YearMonth;
import java.util.List;

public final class InflationDtos {

    private InflationDtos() {}

    public record RatePoint(YearMonth month, BigDecimal percent) {}

    /**
     * Inflación de una moneda. Porcentajes mensuales con 3 decimales.
     * {@code projectedPercent} es la que se usa para los meses sin dato oficial y {@code projectedSource}
     * dice de dónde sale: MANUAL (cargada por el usuario), ESTIMATED (promedio de los últimos
     * {@code estimateMonths} meses oficiales) o NONE (sin datos: 0 %).
     */
    public record CurrencyInflation(
            String currency, String source,
            YearMonth lastOfficialMonth, BigDecimal lastOfficialPercent,
            BigDecimal last12MonthsPercent,
            int estimateMonths, BigDecimal estimatedPercent, BigDecimal manualPercent,
            BigDecimal projectedPercent, String projectedSource,
            Instant lastSync, List<RatePoint> recent) {}

    public record InflationOverview(List<CurrencyInflation> currencies) {}

    public record ManualInflationRequest(
            @NotNull(message = "validation.required")
            @DecimalMin(value = "0", message = "validation.min")
            @DecimalMax(value = "50", message = "validation.max") BigDecimal monthlyPercent) {}
}
