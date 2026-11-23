package com.saver.account;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

public final class AccountDtos {

    private AccountDtos() {}

    /** {@code openingDate}: fecha del saldo inicial (por defecto hoy). */
    public record CreateAccountRequest(
            @NotBlank(message = "validation.required") @Size(max = 60, message = "validation.too_long") String name,
            @NotNull(message = "validation.required") AccountKind kind,
            @NotNull(message = "validation.required") @Pattern(regexp = "ARS|USD", message = "validation.currency") String currency,
            @Digits(integer = 13, fraction = 2, message = "validation.amount") BigDecimal openingBalance,
            LocalDate openingDate,
            @Pattern(regexp = "#[0-9a-fA-F]{6}", message = "validation.color") String color) {}

    public record UpdateAccountRequest(
            @Size(min = 1, max = 60, message = "validation.too_long") String name,
            AccountKind kind,
            @Pattern(regexp = "#[0-9a-fA-F]{6}", message = "validation.color") String color,
            @Digits(integer = 13, fraction = 2, message = "validation.amount") BigDecimal openingBalance,
            LocalDate openingDate,
            Boolean archived) {}

    public record AccountResponse(UUID id, String name, AccountKind kind, String currency, String color,
                                  BigDecimal openingBalance, LocalDate openingDate, BigDecimal balance,
                                  boolean savings, boolean archived) {}

    public record CurrencyTotal(String currency, BigDecimal balance, BigDecimal savings) {}

    /**
     * Patrimonio: saldos por moneda y el total en la moneda principal (los dólares se valúan a MEP compra).
     * {@code netWorth} es null si hay cuentas en otra moneda y no hay cotización guardada.
     */
    public record AccountsOverview(List<AccountResponse> accounts, List<CurrencyTotal> totals, String baseCurrency,
                                   BigDecimal netWorth, BigDecimal netWorthSavings,
                                   BigDecimal valuationRate, String valuationType) {}

    /**
     * {@code amount} en la moneda de la cuenta de origen. Si las monedas difieren se convierte con el dólar MEP
     * (o {@code rate}); {@code toAmount} permite cargar lo que efectivamente llegó.
     */
    public record TransferRequest(
            @NotNull(message = "validation.required") UUID fromAccountId,
            @NotNull(message = "validation.required") UUID toAccountId,
            LocalDate date,
            @NotNull(message = "validation.required") @DecimalMin(value = "0.01", message = "validation.positive")
            @Digits(integer = 13, fraction = 2, message = "validation.amount") BigDecimal amount,
            @DecimalMin(value = "0.01", message = "validation.positive")
            @Digits(integer = 13, fraction = 2, message = "validation.amount") BigDecimal toAmount,
            @DecimalMin(value = "0.0001", message = "validation.positive") BigDecimal rate,
            @Size(max = 500, message = "validation.too_long") String notes) {}

    /** {@code targetBalance}: el saldo real hoy (se registra la diferencia). O bien {@code amount} con signo. */
    public record AdjustmentRequest(
            LocalDate date,
            @Digits(integer = 13, fraction = 2, message = "validation.amount") BigDecimal targetBalance,
            @Digits(integer = 13, fraction = 2, message = "validation.amount") BigDecimal amount,
            @Size(max = 500, message = "validation.too_long") String notes) {}

    public record MovementResponse(LocalDate date, String kind, String description, BigDecimal amount, UUID refId,
                                   YearMonth month, boolean counted) {}
}
