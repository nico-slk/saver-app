package com.saver.expense;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ExpenseRepository extends JpaRepository<Expense, UUID> {

    @Query("select distinct e from Expense e left join fetch e.amounts where e.userId = :userId order by e.startMonth desc")
    List<Expense> findAllWithAmounts(@Param("userId") UUID userId);

    @Query("select e from Expense e left join fetch e.amounts where e.id = :id and e.userId = :userId")
    Optional<Expense> findOwned(@Param("id") UUID id, @Param("userId") UUID userId);
}
