package com.saver.expense;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OccurrenceRepository extends JpaRepository<ExpenseOccurrence, UUID> {

    @Query("select new com.saver.expense.OccurrenceKey(o.expense.id, o.month) from ExpenseOccurrence o where o.userId = :userId")
    List<OccurrenceKey> findKeys(@Param("userId") UUID userId);

    /** Idempotente: si dos requests generan el mismo mes a la vez, la segunda no falla. */
    @Modifying
    @Query(value = """
            INSERT INTO expense_occurrences
              (id, user_id, expense_id, month, installment_number, due_date, expected_amount, paid_amount, currency, status)
            VALUES (:id, :userId, :expenseId, :month, :installment, :dueDate, :amount, 0, :currency, 'PENDING')
            ON CONFLICT (expense_id, month) DO NOTHING
            """, nativeQuery = true)
    int insertIfAbsent(@Param("id") UUID id, @Param("userId") UUID userId, @Param("expenseId") UUID expenseId,
                       @Param("month") LocalDate month, @Param("installment") Integer installment,
                       @Param("dueDate") LocalDate dueDate, @Param("amount") BigDecimal amount,
                       @Param("currency") String currency);

    @Query("""
            select o from ExpenseOccurrence o join fetch o.expense e
            where o.userId = :userId and o.month = :month
            order by o.dueDate, e.description
            """)
    List<ExpenseOccurrence> findMonth(@Param("userId") UUID userId, @Param("month") YearMonth month);

    /** Impagos de meses anteriores (se muestran como vencidos en el mes actual). */
    @Query("""
            select o from ExpenseOccurrence o join fetch o.expense e
            where o.userId = :userId and o.month < :month and o.status <> :paid
            order by o.dueDate, e.description
            """)
    List<ExpenseOccurrence> findUnpaidBefore(@Param("userId") UUID userId, @Param("month") YearMonth month,
                                             @Param("paid") OccurrenceStatus paid);

    /** Ocurrencias de un rango de meses (inclusive), para historial y proyecciones. */
    @Query("""
            select o from ExpenseOccurrence o join fetch o.expense e
            where o.userId = :userId and o.month >= :from and o.month <= :to
            order by o.month, o.dueDate
            """)
    List<ExpenseOccurrence> findRange(@Param("userId") UUID userId, @Param("from") YearMonth from, @Param("to") YearMonth to);

    @Query("select o from ExpenseOccurrence o join fetch o.expense e where o.id = :id and o.userId = :userId")
    Optional<ExpenseOccurrence> findOwned(@Param("id") UUID id, @Param("userId") UUID userId);

    @Query("select o from ExpenseOccurrence o where o.expense.id = :expenseId and o.month >= :from")
    List<ExpenseOccurrence> findFrom(@Param("expenseId") UUID expenseId, @Param("from") YearMonth from);

    @Query("select o from ExpenseOccurrence o where o.expense.id = :expenseId and o.month > :after")
    List<ExpenseOccurrence> findAfter(@Param("expenseId") UUID expenseId, @Param("after") YearMonth after);

    @Query("select count(o) > 0 from ExpenseOccurrence o where o.expense.id = :expenseId and (o.paidAmount > 0 or o.status <> :pending)")
    boolean hasPayments(@Param("expenseId") UUID expenseId, @Param("pending") OccurrenceStatus pending);
}
