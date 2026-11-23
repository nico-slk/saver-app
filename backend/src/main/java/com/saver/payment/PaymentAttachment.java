package com.saver.payment;

import com.saver.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** Comprobante de un pago. El archivo vive en el storage; acá solo la clave y los metadatos. */
@Entity
@Table(name = "payment_attachments")
public class PaymentAttachment extends BaseEntity {

    @Column(name = "user_id", nullable = false) private UUID userId;
    @Column(name = "payment_id", nullable = false) private UUID paymentId;
    @Column(name = "storage_key", nullable = false, length = 300) private String storageKey;
    @Column(name = "file_name", nullable = false, length = 200) private String fileName;
    @Column(name = "content_type", nullable = false, length = 100) private String contentType;
    @Column(name = "size_bytes", nullable = false) private long sizeBytes;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;

    protected PaymentAttachment() {}

    public PaymentAttachment(UUID userId, UUID paymentId, String storageKey, String fileName, String contentType,
                             long sizeBytes, Instant now) {
        this.userId = userId;
        this.paymentId = paymentId;
        this.storageKey = storageKey;
        this.fileName = fileName;
        this.contentType = contentType;
        this.sizeBytes = sizeBytes;
        this.createdAt = now;
    }

    public UUID getUserId() { return userId; }
    public UUID getPaymentId() { return paymentId; }
    public String getStorageKey() { return storageKey; }
    public String getFileName() { return fileName; }
    public String getContentType() { return contentType; }
    public long getSizeBytes() { return sizeBytes; }
}
