package com.saver.inflation;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.YearMonth;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Acceso JDBC a {@code inflation_rates} y {@code user_inflation} (claves compuestas, sin entidades). */
@Repository
public class InflationRepository {

    private final JdbcTemplate jdbc;

    public InflationRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Map<YearMonth, BigDecimal> official(String currency) {
        Map<YearMonth, BigDecimal> result = new LinkedHashMap<>();
        jdbc.query("SELECT month, rate FROM inflation_rates WHERE currency = ? ORDER BY month",
                rs -> { result.put(YearMonth.from(rs.getDate(1).toLocalDate()), rs.getBigDecimal(2)); }, currency);
        return result;
    }

    /** Inserta o actualiza la serie; devuelve la cantidad de filas afectadas. */
    public int upsert(String currency, String source, List<MonthlyRate> rates, Instant now) {
        if (rates.isEmpty()) return 0;
        int[][] counts = jdbc.batchUpdate("""
                INSERT INTO inflation_rates (currency, month, rate, source, fetched_at) VALUES (?, ?, ?, ?, ?)
                ON CONFLICT (currency, month) DO UPDATE SET rate = EXCLUDED.rate, source = EXCLUDED.source,
                    fetched_at = EXCLUDED.fetched_at
                WHERE inflation_rates.rate IS DISTINCT FROM EXCLUDED.rate
                """, rates, 200, (ps, r) -> {
                    ps.setString(1, currency);
                    ps.setDate(2, Date.valueOf(r.month().atDay(1)));
                    ps.setBigDecimal(3, r.rate().setScale(6, java.math.RoundingMode.HALF_UP));
                    ps.setString(4, source);
                    ps.setTimestamp(5, Timestamp.from(now));
                });
        // Filas realmente cambiadas (el WHERE evita reescribir datos iguales). -2 = el driver no informa.
        return java.util.Arrays.stream(counts).flatMapToInt(java.util.Arrays::stream).map(c -> c < 0 ? 1 : c).sum();
    }

    public Optional<Instant> lastFetch(String currency) {
        return jdbc.query("SELECT max(fetched_at) FROM inflation_rates WHERE currency = ?",
                rs -> { rs.next(); Timestamp t = rs.getTimestamp(1); return Optional.ofNullable(t).map(Timestamp::toInstant); },
                currency);
    }

    public Map<String, BigDecimal> manual(UUID userId) {
        Map<String, BigDecimal> result = new LinkedHashMap<>();
        jdbc.query("SELECT currency, monthly_rate FROM user_inflation WHERE user_id = ?",
                rs -> { result.put(rs.getString(1), rs.getBigDecimal(2)); }, userId);
        return result;
    }

    public void setManual(UUID userId, String currency, BigDecimal rate, Instant now) {
        jdbc.update("""
                INSERT INTO user_inflation (user_id, currency, monthly_rate, updated_at) VALUES (?, ?, ?, ?)
                ON CONFLICT (user_id, currency) DO UPDATE SET monthly_rate = EXCLUDED.monthly_rate,
                    updated_at = EXCLUDED.updated_at
                """, userId, currency, rate, Timestamp.from(now));
    }

    public void clearManual(UUID userId, String currency) {
        jdbc.update("DELETE FROM user_inflation WHERE user_id = ? AND currency = ?", userId, currency);
    }
}
