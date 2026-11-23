package com.saver.income;

import java.time.YearMonth;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IncomeReceiptRepository extends JpaRepository<IncomeReceipt, UUID> {
    List<IncomeReceipt> findByIncomeIdAndMonthOrderByReceivedOnAsc(UUID incomeId, YearMonth month);
    List<IncomeReceipt> findByUserIdAndMonth(UUID userId, YearMonth month);
    Optional<IncomeReceipt> findByIdAndUserId(UUID id, UUID userId);
    boolean existsByIncomeId(UUID incomeId);
}
