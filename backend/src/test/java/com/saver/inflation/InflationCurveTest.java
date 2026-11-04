package com.saver.inflation;

import static org.assertj.core.api.Assertions.assertThat;

import com.saver.common.AdjustmentRule;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.Map;
import java.util.TreeMap;
import org.junit.jupiter.api.Test;

class InflationCurveTest {

    private static final YearMonth JAN = YearMonth.of(2026, 1);

    private final Map<YearMonth, BigDecimal> official = Map.of(
            JAN, new BigDecimal("0.03"), JAN.plusMonths(1), new BigDecimal("0.02"),
            JAN.plusMonths(2), new BigDecimal("0.01"));

    @Test
    void estimateIsGeometricMeanOfLastMonths() {
        InflationCurve c = new InflationCurve("ARS", official, 2, null);
        // √(1,02 × 1,01) − 1
        assertThat(c.estimated()).isEqualByComparingTo("0.014988");
        assertThat(c.projectedSource()).isEqualTo(InflationCurve.Source.ESTIMATED);
        assertThat(c.rate(JAN)).isEqualByComparingTo("0.03");
        assertThat(c.rate(JAN.plusMonths(5))).isEqualByComparingTo("0.014988");
    }

    @Test
    void manualReplacesTheEstimateOnlyForMonthsWithoutOfficialData() {
        InflationCurve c = new InflationCurve("ARS", official, 3, new BigDecimal("0.05"));
        assertThat(c.rate(JAN.plusMonths(1))).isEqualByComparingTo("0.02");
        assertThat(c.rate(JAN.plusMonths(3))).isEqualByComparingTo("0.05");
        // 1,01 × 1,05 − 1
        assertThat(c.accumulated(JAN.plusMonths(2), JAN.plusMonths(3))).isEqualByComparingTo("0.0605");
        assertThat(c.withManual(null).projectedSource()).isEqualTo(InflationCurve.Source.ESTIMATED);
    }

    @Test
    void withoutDataInflationIsZero() {
        InflationCurve c = InflationCurve.none("USD");
        assertThat(c.projectedSource()).isEqualTo(InflationCurve.Source.NONE);
        assertThat(c.factor(JAN, JAN.plusMonths(11))).isEqualByComparingTo("1");
    }

    @Test
    void adjustmentWithInflationUsesThePreviousMonths() {
        InflationCurve c = new InflationCurve("ARS", official, 3, new BigDecimal("0.04"));
        AdjustmentRule rule = AdjustmentRule.of((short) 2, null, true);
        BigDecimal base = new BigDecimal("1000");
        assertThat(rule.estimate(base, JAN, JAN.plusMonths(1), c)).isEqualByComparingTo("1000");
        // En marzo: enero y febrero (1,03 × 1,02)
        assertThat(rule.estimate(base, JAN, JAN.plusMonths(2), c)).isEqualByComparingTo("1050.60");
        // En mayo: además marzo (oficial 1 %) y abril (manual 4 %)
        assertThat(rule.estimate(base, JAN, JAN.plusMonths(4), c)).isEqualByComparingTo("1103.55");
        assertThat(rule.next(JAN, JAN.plusMonths(2))).isEqualTo(JAN.plusMonths(4));
        assertThat(AdjustmentRule.of((short) 3, null, false)).isNull();
    }

    @Test
    void blsIndexGapsAreSpreadAcrossMissingMonths() {
        TreeMap<YearMonth, BigDecimal> index = new TreeMap<>(Map.of(
                YearMonth.of(2025, 8), new BigDecimal("100"),
                YearMonth.of(2025, 9), new BigDecimal("101"),
                YearMonth.of(2025, 11), new BigDecimal("103.0301")));
        var rates = BlsInflationProvider.rates(index);
        assertThat(rates).hasSize(3);
        assertThat(rates.get(0).month()).isEqualTo(YearMonth.of(2025, 9));
        assertThat(rates.get(0).rate()).isEqualByComparingTo("0.01");
        assertThat(rates.get(1).month()).isEqualTo(YearMonth.of(2025, 10));
        assertThat(rates.get(1).rate()).isEqualByComparingTo("0.01");
        assertThat(rates.get(2).rate()).isEqualByComparingTo("0.01");
    }
}
