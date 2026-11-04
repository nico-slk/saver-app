package com.saver.income;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface IncomeRepository extends JpaRepository<Income, UUID> {

    @Query("select distinct i from Income i left join fetch i.amounts where i.userId = :userId order by i.startMonth desc")
    List<Income> findAllWithAmounts(@Param("userId") UUID userId);

    /** Con los montos cargados: la respuesta los usa fuera de la transacción. */
    @EntityGraph(attributePaths = "amounts")
    Optional<Income> findByIdAndUserId(UUID id, UUID userId);
}
