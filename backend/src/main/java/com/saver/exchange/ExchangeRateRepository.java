package com.saver.exchange;

import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class ExchangeRateRepository {

    private final JdbcTemplate jdbc;

    public ExchangeRateRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void upsert(List<ExchangeRate> rates) {
        jdbc.batchUpdate("""
                INSERT INTO exchange_rates (day, type, buy, sell, source, fetched_at) VALUES (?, ?, ?, ?, ?, ?)
                ON CONFLICT (day, type) DO UPDATE SET buy = EXCLUDED.buy, sell = EXCLUDED.sell,
                    source = EXCLUDED.source, fetched_at = EXCLUDED.fetched_at
                """, rates, 50, (ps, r) -> {
                    ps.setDate(1, Date.valueOf(r.day()));
                    ps.setString(2, r.type().name());
                    ps.setBigDecimal(3, r.buy());
                    ps.setBigDecimal(4, r.sell());
                    ps.setString(5, r.source());
                    ps.setTimestamp(6, Timestamp.from(r.fetchedAt()));
                });
    }

    /** Última cotización de cada tipo. */
    public Map<ExchangeRateType, ExchangeRate> latest() {
        Map<ExchangeRateType, ExchangeRate> result = new EnumMap<>(ExchangeRateType.class);
        jdbc.query("""
                SELECT DISTINCT ON (type) day, type, buy, sell, source, fetched_at
                FROM exchange_rates ORDER BY type, day DESC
                """, rs -> { ExchangeRate r = map(rs); result.put(r.type(), r); });
        return result;
    }

    public Optional<Instant> lastFetch() {
        return jdbc.query("SELECT max(fetched_at) FROM exchange_rates", rs -> {
            rs.next();
            Timestamp t = rs.getTimestamp(1);
            return Optional.ofNullable(t).map(Timestamp::toInstant);
        });
    }

    private static ExchangeRate map(ResultSet rs) throws SQLException {
        return new ExchangeRate(rs.getDate("day").toLocalDate(), ExchangeRateType.valueOf(rs.getString("type")),
                rs.getBigDecimal("buy"), rs.getBigDecimal("sell"), rs.getString("source"),
                rs.getTimestamp("fetched_at").toInstant());
    }
}
