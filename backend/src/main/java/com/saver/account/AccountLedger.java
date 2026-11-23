package com.saver.account;

import java.math.BigDecimal;
import java.sql.Date;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Movimientos de las cuentas (pagos, cobros, transferencias y ajustes) en una sola consulta.
 * Los montos están en la moneda de la cuenta y con signo (negativo = sale plata).
 * Los movimientos anteriores a la fecha del saldo inicial no modifican el saldo.
 */
@Repository
public class AccountLedger {

    public record Movement(LocalDate date, String kind, String description, BigDecimal amount, UUID refId,
                           YearMonth month, boolean counted) {}

    private static final String MOVEMENTS = """
            WITH mv AS (
              SELECT p.account_id, p.paid_on AS day, -p.account_amount AS amount, 'PAYMENT' AS kind, p.id AS ref_id,
                     e.description AS description, o.month AS month, p.created_at
                FROM payments p JOIN expense_occurrences o ON o.id = p.occurrence_id JOIN expenses e ON e.id = o.expense_id
               WHERE p.user_id = :u AND p.account_id IS NOT NULL
              UNION ALL
              SELECT r.account_id, r.received_on, r.account_amount, 'RECEIPT', r.id, i.description, r.month, r.created_at
                FROM income_receipts r JOIN incomes i ON i.id = r.income_id WHERE r.user_id = :u
              UNION ALL
              SELECT t.from_account_id, t.transfer_on, -t.from_amount, 'TRANSFER_OUT', t.id, a.name, NULL, t.created_at
                FROM transfers t JOIN accounts a ON a.id = t.to_account_id WHERE t.user_id = :u
              UNION ALL
              SELECT t.to_account_id, t.transfer_on, t.to_amount, 'TRANSFER_IN', t.id, a.name, NULL, t.created_at
                FROM transfers t JOIN accounts a ON a.id = t.from_account_id WHERE t.user_id = :u
              UNION ALL
              SELECT d.account_id, d.adjusted_on, d.amount, 'ADJUSTMENT', d.id, d.notes, NULL, d.created_at
                FROM account_adjustments d WHERE d.user_id = :u
            )
            """;

    private final JdbcTemplate jdbc;

    public AccountLedger(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Saldo de cada cuenta del usuario al final del día {@code upTo} (null = todo). */
    public Map<UUID, BigDecimal> balances(UUID userId, LocalDate upTo) {
        Map<UUID, BigDecimal> result = new HashMap<>();
        String sql = MOVEMENTS.replace(":u", "?") + """
                SELECT a.id, a.opening_balance + COALESCE(SUM(mv.amount) FILTER (
                         WHERE mv.day >= a.opening_date AND (CAST(? AS DATE) IS NULL OR mv.day <= CAST(? AS DATE))), 0)
                  FROM accounts a LEFT JOIN mv ON mv.account_id = a.id
                 WHERE a.user_id = ?
                 GROUP BY a.id, a.opening_balance
                """;
        Date limit = upTo == null ? null : Date.valueOf(upTo);
        jdbc.query(sql, rs -> { result.put(rs.getObject(1, UUID.class), rs.getBigDecimal(2)); },
                userId, userId, userId, userId, userId, limit, limit, userId);
        return result;
    }

    /** Últimos movimientos de una cuenta (más nuevos primero). */
    public List<Movement> movements(UUID userId, UUID accountId, LocalDate openingDate, int limit) {
        String sql = MOVEMENTS.replace(":u", "?") + """
                SELECT day, kind, description, amount, ref_id, month FROM mv
                 WHERE account_id = ? ORDER BY day DESC, created_at DESC LIMIT ?
                """;
        return jdbc.query(sql, (rs, i) -> {
            LocalDate day = rs.getDate("day").toLocalDate();
            Date month = rs.getDate("month");
            return new Movement(day, rs.getString("kind"), rs.getString("description"), rs.getBigDecimal("amount"),
                    rs.getObject("ref_id", UUID.class), month == null ? null : YearMonth.from(month.toLocalDate()),
                    !day.isBefore(openingDate));
        }, userId, userId, userId, userId, userId, accountId, limit);
    }

    public boolean hasMovements(UUID accountId) {
        Boolean exists = jdbc.queryForObject("""
                SELECT EXISTS (SELECT 1 FROM payments WHERE account_id = ?)
                    OR EXISTS (SELECT 1 FROM income_receipts WHERE account_id = ?)
                    OR EXISTS (SELECT 1 FROM transfers WHERE from_account_id = ? OR to_account_id = ?)
                    OR EXISTS (SELECT 1 FROM account_adjustments WHERE account_id = ?)
                """, Boolean.class, accountId, accountId, accountId, accountId, accountId);
        return Boolean.TRUE.equals(exists);
    }
}
