package com.saver.common;

import com.saver.inflation.InflationCurve;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.YearMonth;

/**
 * Regla de aumento periódico de un gasto fijo o ingreso: cada {@code everyMonths} meses sube
 * {@code percent} %, o la inflación acumulada de esos meses si {@code withInflation}.
 * Cálculo puro (sin acceso a datos).
 */
public record AdjustmentRule(int everyMonths, BigDecimal percent, boolean withInflation) {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    /** Null si no hay regla completa. */
    public static AdjustmentRule of(Short everyMonths, BigDecimal percent, boolean withInflation) {
        if (everyMonths == null || everyMonths < 1 || (percent == null && !withInflation)) return null;
        return new AdjustmentRule(everyMonths, percent, withInflation);
    }

    /**
     * Monto estimado en {@code month} partiendo de {@code base}, vigente desde {@code effective}.
     * Con inflación: el aumento del mes A es la inflación acumulada de los {@code everyMonths} meses
     * anteriores (A−N … A−1), oficial si existe y proyectada si no. Sin curva, la inflación cuenta como 0.
     */
    public BigDecimal estimate(BigDecimal base, YearMonth effective, YearMonth month, InflationCurve curve) {
        long periods = Months.between(effective, month) / everyMonths;
        if (periods <= 0) return base;
        BigDecimal factor;
        if (withInflation) {
            factor = curve == null ? BigDecimal.ONE : curve.factor(effective, effective.plusMonths(periods * everyMonths - 1));
        } else {
            BigDecimal pct = percent == null ? BigDecimal.ZERO : percent;
            factor = BigDecimal.ONE.add(pct.divide(HUNDRED, MathContext.DECIMAL64)).pow((int) periods, MathContext.DECIMAL64);
        }
        return base.multiply(factor, MathContext.DECIMAL64).setScale(2, RoundingMode.HALF_UP);
    }

    /** Próximo mes de aumento posterior a {@code current} contando desde {@code effective}. */
    public YearMonth next(YearMonth effective, YearMonth current) {
        long elapsed = Math.max(0, Months.between(effective, current));
        return effective.plusMonths((elapsed / everyMonths + 1) * everyMonths);
    }
}
