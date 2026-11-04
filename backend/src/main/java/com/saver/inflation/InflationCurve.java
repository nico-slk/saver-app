package com.saver.inflation;

import java.math.BigDecimal;
import java.math.MathContext;
import java.time.YearMonth;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;

/**
 * Inflación mensual de una moneda, mes a mes (cálculo puro, sin acceso a datos).
 *
 * <p>Para cada mes usa el dato oficial si existe; si no, la tasa proyectada: la manual del usuario
 * si la cargó, si no la estimada (promedio geométrico de los últimos meses oficiales), si no 0.
 * Todas las tasas son fracciones (0.019 = 1,9 %).
 */
public final class InflationCurve {

    public enum Source { MANUAL, ESTIMATED, NONE }

    private static final MathContext MC = MathContext.DECIMAL64;

    private final String currency;
    private final NavigableMap<YearMonth, BigDecimal> official;
    private final BigDecimal estimated;
    private final BigDecimal manual;

    public InflationCurve(String currency, Map<YearMonth, BigDecimal> official, int estimateMonths, BigDecimal manual) {
        this.currency = currency;
        this.official = new TreeMap<>(official);
        this.estimated = estimate(this.official, estimateMonths);
        this.manual = manual;
    }

    public static InflationCurve none(String currency) {
        return new InflationCurve(currency, Map.of(), 3, null);
    }

    /** Misma curva con otra tasa manual (ej. la elegida en la pantalla de proyecciones). */
    public InflationCurve withManual(BigDecimal manualRate) {
        return new InflationCurve(currency, official, estimated, manualRate);
    }

    private InflationCurve(String currency, NavigableMap<YearMonth, BigDecimal> official, BigDecimal estimated,
                           BigDecimal manual) {
        this.currency = currency;
        this.official = official;
        this.estimated = estimated;
        this.manual = manual;
    }

    /** Tasa del mes: oficial si existe, si no la proyectada. */
    public BigDecimal rate(YearMonth month) {
        BigDecimal r = official.get(month);
        return r != null ? r : projectedRate();
    }

    public boolean isOfficial(YearMonth month) {
        return official.containsKey(month);
    }

    /** Tasa usada para los meses sin dato oficial. */
    public BigDecimal projectedRate() {
        if (manual != null) return manual;
        return estimated != null ? estimated : BigDecimal.ZERO;
    }

    public Source projectedSource() {
        if (manual != null) return Source.MANUAL;
        return estimated != null ? Source.ESTIMATED : Source.NONE;
    }

    /**
     * Factor acumulado de los meses {@code from}..{@code to} inclusive: Π (1 + tasa).
     * Devuelve 1 si {@code to} es anterior a {@code from}.
     */
    public BigDecimal factor(YearMonth from, YearMonth to) {
        BigDecimal f = BigDecimal.ONE;
        for (YearMonth m = from; !m.isAfter(to); m = m.plusMonths(1)) {
            f = f.multiply(BigDecimal.ONE.add(rate(m)), MC);
        }
        return f;
    }

    /** Inflación acumulada (fracción) de los meses {@code from}..{@code to} inclusive. */
    public BigDecimal accumulated(YearMonth from, YearMonth to) {
        return factor(from, to).subtract(BigDecimal.ONE);
    }

    public String currency() { return currency; }
    public BigDecimal estimated() { return estimated; }
    public BigDecimal manual() { return manual; }
    public YearMonth lastOfficialMonth() { return official.isEmpty() ? null : official.lastKey(); }
    public NavigableMap<YearMonth, BigDecimal> official() { return java.util.Collections.unmodifiableNavigableMap(official); }

    /** Promedio geométrico de los últimos {@code months} datos oficiales; null si no hay datos. */
    static BigDecimal estimate(NavigableMap<YearMonth, BigDecimal> official, int months) {
        if (official.isEmpty() || months < 1) return null;
        BigDecimal product = BigDecimal.ONE;
        int n = 0;
        for (BigDecimal r : official.descendingMap().values()) {
            if (n == months) break;
            product = product.multiply(BigDecimal.ONE.add(r), MC);
            n++;
        }
        double mean = Math.pow(product.doubleValue(), 1.0 / n) - 1;
        return BigDecimal.valueOf(mean).setScale(6, java.math.RoundingMode.HALF_UP);
    }
}
