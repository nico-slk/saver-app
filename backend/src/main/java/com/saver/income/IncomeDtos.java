package com.saver.income;

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
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

public final class IncomeDtos {

    private IncomeDtos() {}

    /**
     * {@code months}: cantidad de meses que se repite. 1 = puntual. null = indefinido.
     * FIXED es indefinido salvo que se indique {@code months}.
     */
    public record CreateIncomeRequest(
            @NotBlank(message = "validation.required") @Size(max = 120, message = "validation.too_long") String description,
            @NotNull(message = "validation.required") IncomeKind kind,
            @NotNull(message = "validation.required") @Pattern(regexp = "ARS|USD", message = "validation.currency") String currency,
            @NotNull(message = "validation.required") @DecimalMin(value = "0.01", message = "validation.positive")
            @Digits(integer = 13, fraction = 2, message = "validation.amount") BigDecimal amount,
            @NotNull(message = "validation.required") YearMonth startMonth,
            @Min(value = 1, message = "validation.min") @Max(value = 600, message = "validation.max") Integer months,
            @Min(value = 1, message = "validation.min") @Max(value = 120, message = "validation.max") Short adjustEveryMonths,
            @DecimalMin(value = "0", message = "validation.min") @DecimalMax(value = "1000", message = "validation.max") BigDecimal adjustPercent,
            boolean adjustWithInflation) {}

    /**
     * endMonth explícito para dar de baja un ingreso; {@code clearEnd} lo vuelve indefinido.
     * {@code clearAdjustment} quita la regla de aumentos.
     */
    public record UpdateIncomeRequest(
            @Size(min = 1, max = 120, message = "validation.too_long") String description,
            YearMonth endMonth,
            Boolean clearEnd,
            @Min(value = 1, message = "validation.min") @Max(value = 120, message = "validation.max") Short adjustEveryMonths,
            @DecimalMin(value = "0", message = "validation.min") @DecimalMax(value = "1000", message = "validation.max") BigDecimal adjustPercent,
            Boolean adjustWithInflation,
            Boolean clearAdjustment) {}

    public record AmountChangeRequest(
            @NotNull(message = "validation.required") YearMonth effectiveMonth,
            @NotNull(message = "validation.required") @DecimalMin(value = "0.01", message = "validation.positive")
            @Digits(integer = 13, fraction = 2, message = "validation.amount") BigDecimal amount) {}

    public record AmountVersion(YearMonth effectiveMonth, BigDecimal amount) {}

    public record IncomeResponse(
            UUID id, String description, IncomeKind kind, String currency,
            YearMonth startMonth, YearMonth endMonth, boolean recurring,
            BigDecimal currentAmount, List<AmountVersion> amounts,
            Short adjustEveryMonths, BigDecimal adjustPercent, boolean adjustWithInflation, YearMonth nextAdjustmentMonth) {}
}
