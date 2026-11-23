package com.saver.payment;

import com.saver.common.ApiException;
import com.saver.config.AppProperties;
import com.saver.payment.PaymentDtos.AttachmentResponse;
import com.saver.storage.StorageService;
import com.saver.storage.StorageUpload;
import com.saver.storage.StoredObject;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

/**
 * Comprobantes de pagos (PDF, PNG, JPEG, WEBP). El tipo se decide por el contenido del archivo,
 * no por lo que declara el navegador. Máximo {@value #MAX_PER_PAYMENT} por pago.
 */
@Service
public class AttachmentService {

    private static final Logger log = LoggerFactory.getLogger(AttachmentService.class);
    static final int MAX_PER_PAYMENT = 10;

    /** Archivo abierto para descargar. */
    public record Download(String fileName, String contentType, long size, InputStream content) {}

    private final PaymentAttachmentRepository attachments;
    private final PaymentRepository payments;
    private final StorageService storage;
    private final long maxSize;
    private final Clock clock;

    public AttachmentService(PaymentAttachmentRepository attachments, PaymentRepository payments, StorageService storage,
                             AppProperties props, Clock clock) {
        this.attachments = attachments;
        this.payments = payments;
        this.storage = storage;
        this.maxSize = props.storage().maxFileSizeBytes();
        this.clock = clock;
    }

    @Transactional
    public List<AttachmentResponse> add(UUID userId, UUID paymentId, List<MultipartFile> files) {
        Payment p = payments.findByIdAndUserId(paymentId, userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "payment.not_found"));
        if (files == null || files.isEmpty()) throw new ApiException(HttpStatus.BAD_REQUEST, "attachment.required");
        if (attachments.countByPaymentId(p.getId()) + files.size() > MAX_PER_PAYMENT) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "attachment.too_many");
        }
        List<AttachmentResponse> result = new ArrayList<>();
        List<String> stored = new ArrayList<>();
        try {
            for (MultipartFile f : files) {
                if (f.getSize() > maxSize) throw new ApiException(HttpStatus.BAD_REQUEST, "attachment.too_large");
                byte[] bytes = f.getBytes();
                String type = sniff(bytes);
                if (type == null) throw new ApiException(HttpStatus.BAD_REQUEST, "attachment.invalid_type");
                StoredObject obj = storage.store(new StorageUpload(new ByteArrayInputStream(bytes), bytes.length, type));
                stored.add(obj.storageKey());
                PaymentAttachment a = attachments.save(new PaymentAttachment(userId, p.getId(), obj.storageKey(),
                        cleanName(f.getOriginalFilename(), type), type, bytes.length, clock.instant()));
                result.add(toResponse(a));
            }
        } catch (IOException e) {
            stored.forEach(this::deleteQuietly);
            throw new ApiException(HttpStatus.BAD_REQUEST, "attachment.unreadable");
        } catch (RuntimeException e) {
            stored.forEach(this::deleteQuietly);
            throw e;
        }
        // Si la transacción falla después, los archivos quedarían huérfanos: se borran.
        afterRollback(() -> stored.forEach(this::deleteQuietly));
        return result;
    }

    @Transactional(readOnly = true)
    public Download open(UUID userId, UUID attachmentId) {
        PaymentAttachment a = owned(userId, attachmentId);
        return new Download(a.getFileName(), a.getContentType(), a.getSizeBytes(), storage.open(a.getStorageKey()));
    }

    @Transactional
    public void delete(UUID userId, UUID attachmentId) {
        PaymentAttachment a = owned(userId, attachmentId);
        attachments.delete(a);
        afterCommit(() -> deleteQuietly(a.getStorageKey()));
    }

    /** Borra los archivos de los comprobantes (la fila se borra en cascada con el pago). */
    void deleteFilesOf(List<PaymentAttachment> list) {
        List<String> keys = list.stream().map(PaymentAttachment::getStorageKey).toList();
        afterCommit(() -> keys.forEach(this::deleteQuietly));
    }

    static AttachmentResponse toResponse(PaymentAttachment a) {
        return new AttachmentResponse(a.getId(), a.getFileName(), a.getContentType(), a.getSizeBytes());
    }

    /** Tipo real según los primeros bytes; null si no es un formato permitido. */
    static String sniff(byte[] b) {
        if (startsWith(b, 0x25, 0x50, 0x44, 0x46)) return "application/pdf";             // %PDF
        if (startsWith(b, 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)) return "image/png";
        if (startsWith(b, 0xFF, 0xD8, 0xFF)) return "image/jpeg";
        if (b.length >= 12 && startsWith(b, 0x52, 0x49, 0x46, 0x46)                       // RIFF....WEBP
                && b[8] == 0x57 && b[9] == 0x45 && b[10] == 0x42 && b[11] == 0x50) return "image/webp";
        return null;
    }

    private static boolean startsWith(byte[] b, int... sig) {
        if (b.length < sig.length) return false;
        for (int i = 0; i < sig.length; i++) if ((b[i] & 0xFF) != sig[i]) return false;
        return true;
    }

    /** Solo el nombre (sin rutas) y sin caracteres de control; se usa para mostrar y descargar. */
    static String cleanName(String original, String type) {
        String name = original == null ? "" : original.replace('\\', '/');
        name = name.substring(name.lastIndexOf('/') + 1).replaceAll("[\\p{Cntrl}\"]", "").trim();
        if (name.isEmpty()) name = "comprobante." + type.substring(type.indexOf('/') + 1);
        return name.length() > 200 ? name.substring(name.length() - 200) : name;
    }

    private PaymentAttachment owned(UUID userId, UUID id) {
        return attachments.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "attachment.not_found"));
    }

    private void deleteQuietly(String key) {
        try {
            storage.delete(key);
        } catch (RuntimeException e) {
            log.warn("Could not delete stored file {}: {}", key, e.getMessage());
        }
    }

    private static void afterCommit(Runnable r) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) { r.run(); return; }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCommit() { r.run(); }
        });
    }

    private static void afterRollback(Runnable r) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) return;
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCompletion(int status) { if (status == STATUS_ROLLED_BACK) r.run(); }
        });
    }
}
