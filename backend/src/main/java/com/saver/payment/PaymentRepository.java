package com.saver.payment;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PaymentRepository extends JpaRepository<Payment, UUID> {

    List<Payment> findByOccurrenceIdOrderByPaidOnAscCreatedAtAsc(UUID occurrenceId);

    Optional<Payment> findByIdAndUserId(UUID id, UUID userId);

    @Query("select coalesce(sum(p.amount), 0) from Payment p where p.occurrenceId = :occurrenceId")
    BigDecimal totalFor(@Param("occurrenceId") UUID occurrenceId);

    /** Pagos reales (no asumidos) de algún mes del gasto: impiden borrarlo. */
    @Query(value = """
            SELECT EXISTS (SELECT 1 FROM payments p JOIN expense_occurrences o ON o.id = p.occurrence_id
                            WHERE o.expense_id = :expenseId AND NOT p.assumed)
            """, nativeQuery = true)
    boolean existsRealForExpense(@Param("expenseId") UUID expenseId);
}
