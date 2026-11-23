package com.saver.expense;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

public final class ExpenseDtos {

    private ExpenseDtos() {}

    /**
     * {@code amount}: monto por mes (en cuotas, el valor de cada cuota).
     * {@code installments}: obligatorio para INSTALLMENTS. {@code dueDay}: null = último día del mes.
     */
    public record CreateExpenseRequest(
            @NotBlank(message = "validation.required") @Size(max = 120, message = "validation.too_long") String description,
            @NotNull(message = "validation.required") UUID categoryId,
            @NotNull(message = "validation.required") ExpenseKind kind,
            @NotNull(message = "validation.required") @Pattern(regexp = "ARS|USD", message = "validation.currency") String currency,
            @NotNull(message = "validation.required") @DecimalMin(value = "0.01", message = "validation.positive")
            @Digits(integer = 13, fraction = 2, message = "validation.amount") BigDecimal amount,
            @NotNull(message = "validation.required") YearMonth startMonth,
            @Min(value = 1, message = "validation.min") @Max(value = 360, message = "validation.max") Integer installments,
            @Min(value = 1, message = "validation.min") @Max(value = 31, message = "validation.max") Short dueDay,
            boolean variableAmount,
            @Min(value = 1, message = "validation.min") @Max(value = 120, message = "validation.max") Short adjustEveryMonths,
            @DecimalMin(value = "0", message = "validation.min") @DecimalMax(value = "1000", message = "validation.max") BigDecimal adjustPercent,
            boolean adjustWithInflation,
            @Size(max = 500, message = "validation.too_long") String notes,
            UUID cardId,
            /**
             * Obligatorio si el gasto empieza antes del mes actual: true = los meses anteriores ya están pagos
             * (se registran pagos "asumidos", sin cuenta ni comprobante); false = quedan pendientes/vencidos.
             */
            Boolean pastMonthsPaid) {}

    /**
     * Edición de una plantilla. Tipo, moneda, inicio y cantidad de cuotas no se editan (borrar y crear).
     * {@code dueDay = 0} vuelve al último día del mes. {@code clearAdjustment} quita la regla de aumentos.
     */
    public record UpdateExpenseRequest(
            @Size(min = 1, max = 120, message = "validation.too_long") String description,
            UUID categoryId,
            @Min(value = 0, message = "validation.min") @Max(value = 31, message = "validation.max") Short dueDay,
            Boolean variableAmount,
            @Min(value = 1, message = "validation.min") @Max(value = 120, message = "validation.max") Short adjustEveryMonths,
            @DecimalMin(value = "0", message = "validation.min") @DecimalMax(value = "1000", message = "validation.max") BigDecimal adjustPercent,
            Boolean adjustWithInflation,
            Boolean clearAdjustment,
            YearMonth endMonth,
            Boolean clearEnd,
            @Size(max = 500, message = "validation.too_long") String notes,
            UUID cardId,
            Boolean clearCard) {}

    public record AmountChangeRequest(
            @NotNull(message = "validation.required") YearMonth effectiveMonth,
            @NotNull(message = "validation.required") @DecimalMin(value = "0.01", message = "validation.positive")
            @Digits(integer = 13, fraction = 2, message = "validation.amount") BigDecimal amount) {}

    public record AmountVersion(YearMonth effectiveMonth, BigDecimal amount) {}

    public record ExpenseResponse(
            UUID id, String description, UUID categoryId, ExpenseKind kind, String currency,
            YearMonth startMonth, YearMonth endMonth, Integer installments, Integer remainingInstallments,
            Short dueDay, boolean variableAmount,
            Short adjustEveryMonths, BigDecimal adjustPercent, boolean adjustWithInflation, YearMonth nextAdjustmentMonth,
            BigDecimal currentAmount, BigDecimal totalAmount, List<AmountVersion> amounts, String notes, UUID cardId) {}

    /** Edición puntual de una ocurrencia (ej. monto real de la factura de luz, otra fecha de vencimiento). */
    public record UpdateOccurrenceRequest(
            LocalDate dueDate,
            @DecimalMin(value = "0.01", message = "validation.positive")
            @Digits(integer = 13, fraction = 2, message = "validation.amount") BigDecimal expectedAmount,
            @Size(max = 500, message = "validation.too_long") String notes) {}

    /**
     * Estado mostrado: PENDING, PARTIAL, PAID u OVERDUE (vencido y no pagado).
     * {@code projected} = mes futuro calculado, todavía no generado (no se puede editar ni pagar).
     */
    public record OccurrenceResponse(
            UUID id, UUID expenseId, String description, UUID categoryId, ExpenseKind kind,
            YearMonth month, LocalDate dueDate, long daysLeft,
            Integer installmentNumber, Integer installments,
            BigDecimal expectedAmount, BigDecimal paidAmount, String currency,
            String status, boolean variableAmount, boolean projected, String notes, UUID cardId) {}
}
