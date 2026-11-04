package com.saver.inflation;

import com.saver.common.ApiException;
import com.saver.inflation.InflationDtos.CurrencyInflation;
import com.saver.inflation.InflationDtos.InflationOverview;
import com.saver.inflation.InflationDtos.RatePoint;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Inflación por moneda: datos oficiales guardados + estimación + valor manual del usuario. */
@Service
public class InflationService {

    public static final List<String> CURRENCIES = List.of("ARS", "USD");
    private static final Map<String, String> SOURCES = Map.of("ARS", "INDEC", "USD", "BLS CPI-U");

    private final InflationRepository repository;
    private final InflationProperties props;
    private final Clock clock;

    public InflationService(InflationRepository repository, InflationProperties props, Clock clock) {
        this.repository = repository;
        this.props = props;
        this.clock = clock;
    }

    /** Curva de una moneda para el usuario (con su inflación manual si la cargó). */
    @Transactional(readOnly = true)
    public InflationCurve curve(UUID userId, String currency) {
        return new InflationCurve(currency, repository.official(currency), props.estimateMonths(),
                repository.manual(userId).get(currency));
    }

    /** Curvas por moneda, cargadas a demanda (para cálculos con gastos en varias monedas). */
    public Curves curves(UUID userId) {
        return new Curves(userId);
    }

    public final class Curves {
        private final UUID userId;
        private final Map<String, InflationCurve> cache = new HashMap<>();
        private Curves(UUID userId) { this.userId = userId; }
        public InflationCurve of(String currency) { return cache.computeIfAbsent(currency, c -> curve(userId, c)); }
    }

    @Transactional(readOnly = true)
    public InflationOverview overview(UUID userId) {
        List<CurrencyInflation> list = new ArrayList<>();
        for (String c : CURRENCIES) {
            InflationCurve curve = curve(userId, c);
            YearMonth last = curve.lastOfficialMonth();
            List<RatePoint> recent = curve.official().descendingMap().entrySet().stream().limit(13)
                    .map(e -> new RatePoint(e.getKey(), percent(e.getValue()))).toList().reversed();
            BigDecimal last12 = last == null || curve.official().size() < 12 ? null
                    : percent(curve.accumulated(last.minusMonths(11), last));
            list.add(new CurrencyInflation(c, SOURCES.get(c), last,
                    last == null ? null : percent(curve.official().get(last)), last12,
                    props.estimateMonths(), percent(curve.estimated()), percent(curve.manual()),
                    percent(curve.projectedRate()), curve.projectedSource().name(),
                    repository.lastFetch(c).orElse(null), recent));
        }
        return new InflationOverview(list);
    }

    @Transactional
    public void setManual(UUID userId, String currency, BigDecimal monthlyPercent) {
        repository.setManual(userId, validCurrency(currency),
                monthlyPercent.movePointLeft(2).setScale(5, RoundingMode.HALF_UP), clock.instant());
    }

    @Transactional
    public void clearManual(UUID userId, String currency) {
        repository.clearManual(userId, validCurrency(currency));
    }

    public static String validCurrency(String currency) {
        if (currency == null || !CURRENCIES.contains(currency)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "inflation.invalid_currency");
        }
        return currency;
    }

    /** Fracción → porcentaje con 3 decimales (null-safe). */
    public static BigDecimal percent(BigDecimal fraction) {
        return fraction == null ? null : fraction.movePointRight(2).setScale(3, RoundingMode.HALF_UP);
    }
}
