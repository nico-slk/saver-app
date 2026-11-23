package com.saver.card;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CardRepository extends JpaRepository<Card, UUID> {
    List<Card> findByUserIdOrderByCreatedAtAsc(UUID userId);
    Optional<Card> findByIdAndUserId(UUID id, UUID userId);
}
