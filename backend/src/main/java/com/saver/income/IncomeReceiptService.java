package com.saver.income;

import com.saver.account.Account;
import com.saver.account.AccountRepository;
import com.saver.account.AccountService;
import com.saver.common.ApiException;
import com.saver.common.Money;
import com.saver.exchange.ExchangeRateService;
import com.saver.exchange.ExchangeRateService.Conversion;
import com.saver.exchange.ExchangeRateService.Purpose;
import com.saver.user.UserTime;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** "Cobrar": registra que un ingreso del mes entró a una cuenta (total o en partes). */
@Service
public class IncomeReceiptService {

    /** {@code amount} en la moneda del ingreso; {@code rate} = pesos por dólar si la cuenta es de otra moneda. */
    public record ReceiptRequest(
            @NotNull(message = "validation.required") YearMonth month,
            @NotNull(message = "validation.required") UUID accountId,
            LocalDate date,
            @NotNull(message = "validation.required") @DecimalMin(value = "0.01", message = "validation.positive")
            @Digits(integer = 13, fraction = 2, message = "validation.amount") BigDecimal amount,
            @DecimalMin(value = "0.0001", message = "validation.positive") BigDecimal rate,
            @Size(max = 500, message = "validation.too_long") String notes) {}

    public record ReceiptResponse(UUID id, UUID incomeId, YearMonth month, UUID accountId, String accountName,
                                  String accountCurrency, LocalDate receivedOn, BigDecimal amount, String currency,
                                  BigDecimal accountAmount, BigDecimal rate, String rateType, String notes) {}

    private final IncomeReceiptRepository receipts;
    private final IncomeRepository incomes;
    private final AccountService accountService;
    private final AccountRepository accounts;
    private final ExchangeRateService rates;
    private final UserTime time;
    private final Clock clock;

    public IncomeReceiptService(IncomeReceiptRepository receipts, IncomeRepository incomes, AccountService accountService,
                                AccountRepository accounts, ExchangeRateService rates, UserTime time, Clock clock) {
        this.receipts = receipts;
        this.incomes = incomes;
        this.accountService = accountService;
        this.accounts = accounts;
        this.rates = rates;
        this.time = time;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<ReceiptResponse> list(UUID userId, UUID incomeId, YearMonth month) {
        Income i = owned(userId, incomeId);
        return responses(i, receipts.findByIncomeIdAndMonthOrderByReceivedOnAsc(i.getId(), month));
    }

    @Transactional
    public List<ReceiptResponse> receive(UUID userId, UUID incomeId, ReceiptRequest req) {
        Income i = owned(userId, incomeId);
        if (!i.activeIn(req.month())) throw ApiException.field("month", "receipt.month_inactive");
        Account a = accountService.usable(userId, req.accountId());
        BigDecimal amount = Money.of(req.amount());
        Conversion c = rates.convert(amount, i.getCurrency(), a.getCurrency(), Purpose.RECEIPT, req.rate());
        receipts.save(new IncomeReceipt(userId, i.getId(), req.month(), a.getId(),
                req.date() == null ? time.today(userId) : req.date(), amount, c.amount(), c.rate(), c.type(),
                req.notes() == null || req.notes().isBlank() ? null : req.notes().trim(), clock.instant()));
        return responses(i, receipts.findByIncomeIdAndMonthOrderByReceivedOnAsc(i.getId(), req.month()));
    }

    @Transactional
    public void delete(UUID userId, UUID receiptId) {
        receipts.delete(receipts.findByIdAndUserId(receiptId, userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "receipt.not_found")));
    }

    /** Total cobrado en el mes por ingreso (en la moneda del ingreso). */
    @Transactional(readOnly = true)
    public Map<UUID, BigDecimal> receivedIn(UUID userId, YearMonth month) {
        Map<UUID, BigDecimal> result = new HashMap<>();
        receipts.findByUserIdAndMonth(userId, month).forEach(r -> result.merge(r.getIncomeId(), r.getAmount(), BigDecimal::add));
        return result;
    }

    private List<ReceiptResponse> responses(Income i, List<IncomeReceipt> list) {
        Map<UUID, Account> accs = new HashMap<>();
        accounts.findAllById(list.stream().map(IncomeReceipt::getAccountId).toList()).forEach(a -> accs.put(a.getId(), a));
        return list.stream().map(r -> {
            Account a = accs.get(r.getAccountId());
            return new ReceiptResponse(r.getId(), i.getId(), r.getMonth(), r.getAccountId(), a == null ? null : a.getName(),
                    a == null ? null : a.getCurrency(), r.getReceivedOn(), r.getAmount(), i.getCurrency(),
                    r.getAccountAmount(), r.getRate(), r.getRateType(), r.getNotes());
        }).toList();
    }

    private Income owned(UUID userId, UUID id) {
        return incomes.findByIdAndUserId(id, userId).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "income.not_found"));
    }
}
