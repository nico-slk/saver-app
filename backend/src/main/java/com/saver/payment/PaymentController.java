package com.saver.payment;

import com.saver.payment.AttachmentService.Download;
import com.saver.payment.PaymentDtos.AttachmentResponse;
import com.saver.payment.PaymentDtos.OccurrencePayments;
import com.saver.payment.PaymentDtos.PaymentRequest;
import com.saver.security.CurrentUser;
import jakarta.validation.Valid;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api")
public class PaymentController {

    private final PaymentService payments;
    private final AttachmentService attachments;

    public PaymentController(PaymentService payments, AttachmentService attachments) {
        this.payments = payments;
        this.attachments = attachments;
    }

    @GetMapping("/occurrences/{id}/payments")
    public OccurrencePayments list(@CurrentUser UUID userId, @PathVariable UUID id) {
        return payments.list(userId, id);
    }

    @PostMapping("/occurrences/{id}/payments")
    @ResponseStatus(HttpStatus.CREATED)
    public OccurrencePayments pay(@CurrentUser UUID userId, @PathVariable UUID id, @Valid @RequestBody PaymentRequest req) {
        return payments.pay(userId, id, req);
    }

    @DeleteMapping("/payments/{id}")
    public OccurrencePayments delete(@CurrentUser UUID userId, @PathVariable UUID id) {
        return payments.delete(userId, id);
    }

    @PostMapping(value = "/payments/{id}/attachments", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public List<AttachmentResponse> upload(@CurrentUser UUID userId, @PathVariable UUID id,
                                           @RequestParam("files") List<MultipartFile> files) {
        return attachments.add(userId, id, files);
    }

    /** Descarga a través de la API (con sesión): funciona igual con cualquier proveedor de storage. */
    @GetMapping("/attachments/{id}")
    public ResponseEntity<InputStreamResource> download(@CurrentUser UUID userId, @PathVariable UUID id) {
        Download d = attachments.open(userId, id);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(d.contentType()))
                .contentLength(d.size())
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.inline().filename(d.fileName(), StandardCharsets.UTF_8).build().toString())
                .header("X-Content-Type-Options", "nosniff")
                .header(HttpHeaders.CACHE_CONTROL, "private, max-age=300")
                .body(new InputStreamResource(d.content()));
    }

    @DeleteMapping("/attachments/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteAttachment(@CurrentUser UUID userId, @PathVariable UUID id) {
        attachments.delete(userId, id);
    }
}
