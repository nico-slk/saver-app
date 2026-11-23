package com.saver.account;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AccountAdjustmentRepository extends JpaRepository<AccountAdjustment, UUID> {
    Optional<AccountAdjustment> findByIdAndUserId(UUID id, UUID userId);
}
