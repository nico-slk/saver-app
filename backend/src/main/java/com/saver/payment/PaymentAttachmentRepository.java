package com.saver.payment;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentAttachmentRepository extends JpaRepository<PaymentAttachment, UUID> {
    List<PaymentAttachment> findByPaymentIdIn(Collection<UUID> paymentIds);
    long countByPaymentId(UUID paymentId);
    Optional<PaymentAttachment> findByIdAndUserId(UUID id, UUID userId);
}
