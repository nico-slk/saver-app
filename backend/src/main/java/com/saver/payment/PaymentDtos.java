package com.saver.payment;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class PaymentDtos {

    private PaymentDtos() {}

    /**
     * {@code amount} en la moneda del gasto. Si la cuenta es de otra moneda se convierte con el dólar que
     * corresponda (tarjeta si el gasto tiene tarjeta, si no oficial) o con {@code rate} (pesos por dólar).
     */
    public record PaymentRequest(
            @NotNull(message = "validation.required") UUID accountId,
            LocalDate date,
            @NotNull(message = "validation.required") @DecimalMin(value = "0.01", message = "validation.positive")
            @Digits(integer = 13, fraction = 2, message = "validation.amount") BigDecimal amount,
            @DecimalMin(value = "0.0001", message = "validation.positive") BigDecimal rate,
            @Size(max = 500, message = "validation.too_long") String notes) {}

    public record AttachmentResponse(UUID id, String fileName, String contentType, long sizeBytes) {}

    public record PaymentResponse(UUID id, UUID occurrenceId, UUID accountId, String accountName, String accountCurrency,
                                  LocalDate paidOn, BigDecimal amount, String currency, BigDecimal accountAmount,
                                  BigDecimal rate, String rateType, String notes, boolean assumed,
                                  List<AttachmentResponse> attachments) {}

    /** Pagos de una ocurrencia con su estado actualizado. */
    public record OccurrencePayments(UUID occurrenceId, String status, BigDecimal expectedAmount, BigDecimal paidAmount,
                                     BigDecimal remaining, String currency, UUID cardId, List<PaymentResponse> payments) {}
}
