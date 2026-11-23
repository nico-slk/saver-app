package com.saver.account;

import com.saver.account.AccountDtos.AccountResponse;
import com.saver.account.AccountDtos.AccountsOverview;
import com.saver.account.AccountDtos.AdjustmentRequest;
import com.saver.account.AccountDtos.CreateAccountRequest;
import com.saver.account.AccountDtos.CurrencyTotal;
import com.saver.account.AccountDtos.MovementResponse;
import com.saver.account.AccountDtos.TransferRequest;
import com.saver.account.AccountDtos.UpdateAccountRequest;
import com.saver.common.ApiException;
import com.saver.common.Money;
import com.saver.exchange.ExchangeRateService;
import com.saver.exchange.ExchangeRateService.Conversion;
import com.saver.exchange.ExchangeRateService.Purpose;
import com.saver.user.UserRepository;
import com.saver.user.UserTime;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccountService {

    static final String DEFAULT_COLOR = "#2a78d6";

    private final AccountRepository accounts;
    private final TransferRepository transfers;
    private final AccountAdjustmentRepository adjustments;
    private final AccountLedger ledger;
    private final ExchangeRateService rates;
    private final UserRepository users;
    private final UserTime time;
    private final Clock clock;

    public AccountService(AccountRepository accounts, TransferRepository transfers, AccountAdjustmentRepository adjustments,
                          AccountLedger ledger, ExchangeRateService rates, UserRepository users, UserTime time, Clock clock) {
        this.accounts = accounts;
        this.transfers = transfers;
        this.adjustments = adjustments;
        this.ledger = ledger;
        this.rates = rates;
        this.users = users;
        this.time = time;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public AccountsOverview overview(UUID userId) {
        List<Account> list = accounts.findByUserIdOrderByCreatedAtAsc(userId);
        Map<UUID, BigDecimal> balances = ledger.balances(userId, null);
        String base = users.findById(userId).map(u -> u.getBaseCurrency()).orElse("ARS");
        List<AccountResponse> items = list.stream().map(a -> toResponse(a, balances.get(a.getId()))).toList();

        Map<String, BigDecimal[]> byCurrency = new TreeMap<>();
        for (AccountResponse a : items) {
            if (a.archived()) continue;
            BigDecimal[] t = byCurrency.computeIfAbsent(a.currency(), k -> new BigDecimal[]{Money.ZERO, Money.ZERO});
            t[0] = t[0].add(a.balance());
            if (a.savings()) t[1] = t[1].add(a.balance());
        }
        List<CurrencyTotal> totals = byCurrency.entrySet().stream()
                .map(e -> new CurrencyTotal(e.getKey(), e.getValue()[0], e.getValue()[1])).toList();

        BigDecimal netWorth = Money.ZERO, savings = Money.ZERO, rate = null;
        String type = null;
        for (CurrencyTotal t : totals) {
            if (t.currency().equals(base)) {
                netWorth = netWorth.add(t.balance());
                savings = savings.add(t.savings());
                continue;
            }
            try {
                Conversion c = rates.convert(t.balance().abs(), t.currency(), base, Purpose.VALUATION, null);
                Conversion s = rates.convert(t.savings().abs(), t.currency(), base, Purpose.VALUATION, null);
                netWorth = netWorth.add(t.balance().signum() < 0 ? c.amount().negate() : c.amount());
                savings = savings.add(t.savings().signum() < 0 ? s.amount().negate() : s.amount());
                rate = c.rate();
                type = c.type();
            } catch (ApiException e) {
                netWorth = null;
                savings = null;
                break;
            }
        }
        return new AccountsOverview(items, totals, base, netWorth, savings, rate, type);
    }

    @Transactional
    public Account create(UUID userId, CreateAccountRequest req) {
        Account a = new Account(userId, req.name().trim(), req.kind(), req.currency(),
                Money.of(req.openingBalance() == null ? BigDecimal.ZERO : req.openingBalance()),
                req.openingDate() == null ? time.today(userId) : req.openingDate(),
                req.color() == null ? DEFAULT_COLOR : req.color());
        return accounts.save(a);
    }

    @Transactional
    public Account update(UUID userId, UUID id, UpdateAccountRequest req) {
        Account a = owned(userId, id);
        if (req.name() != null) a.setName(req.name().trim());
        if (req.kind() != null) a.setKind(req.kind());
        if (req.color() != null) a.setColor(req.color());
        if (req.openingBalance() != null) a.setOpeningBalance(Money.of(req.openingBalance()));
        if (req.openingDate() != null) a.setOpeningDate(req.openingDate());
        if (req.archived() != null) a.setArchivedAt(req.archived() ? clock.instant() : null);
        return a;
    }

    /** Solo se borran cuentas sin movimientos; las demás se archivan. */
    @Transactional
    public void delete(UUID userId, UUID id) {
        Account a = owned(userId, id);
        if (ledger.hasMovements(a.getId())) throw new ApiException(HttpStatus.CONFLICT, "account.has_movements");
        accounts.delete(a);
    }

    @Transactional(readOnly = true)
    public List<MovementResponse> movements(UUID userId, UUID id, int limit) {
        Account a = owned(userId, id);
        return ledger.movements(userId, id, a.getOpeningDate(), Math.min(Math.max(limit, 1), 500)).stream()
                .map(m -> new MovementResponse(m.date(), m.kind(), m.description(), m.amount(), m.refId(), m.month(), m.counted()))
                .toList();
    }

    @Transactional
    public Transfer transfer(UUID userId, TransferRequest req) {
        if (req.fromAccountId().equals(req.toAccountId())) throw new ApiException(HttpStatus.BAD_REQUEST, "transfer.same_account");
        Account from = usable(userId, req.fromAccountId());
        Account to = usable(userId, req.toAccountId());
        BigDecimal amount = Money.of(req.amount());
        BigDecimal toAmount;
        BigDecimal rate = null;
        String type = null;
        if (from.getCurrency().equals(to.getCurrency())) {
            toAmount = amount;
        } else if (req.toAmount() != null) {
            toAmount = Money.of(req.toAmount());
            BigDecimal ars = "ARS".equals(from.getCurrency()) ? amount : toAmount;
            BigDecimal usd = "USD".equals(from.getCurrency()) ? amount : toAmount;
            rate = ars.divide(usd, 4, RoundingMode.HALF_UP);
            type = "MANUAL";
        } else {
            Conversion c = rates.convert(amount, from.getCurrency(), to.getCurrency(), Purpose.TRANSFER, req.rate());
            toAmount = c.amount();
            rate = c.rate();
            type = c.type();
        }
        LocalDate date = req.date() == null ? time.today(userId) : req.date();
        return transfers.save(new Transfer(userId, from.getId(), to.getId(), date, amount, toAmount, rate, type,
                blankToNull(req.notes()), clock.instant()));
    }

    @Transactional
    public void deleteTransfer(UUID userId, UUID id) {
        transfers.delete(transfers.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "transfer.not_found")));
    }

    @Transactional
    public AccountAdjustment adjust(UUID userId, UUID accountId, AdjustmentRequest req) {
        Account a = usable(userId, accountId);
        BigDecimal amount;
        if (req.targetBalance() != null) {
            BigDecimal current = ledger.balances(userId, null).getOrDefault(a.getId(), a.getOpeningBalance());
            amount = Money.of(req.targetBalance()).subtract(current);
        } else if (req.amount() != null) {
            amount = Money.of(req.amount());
        } else {
            throw new ApiException(HttpStatus.BAD_REQUEST, "account.adjustment_required");
        }
        if (amount.signum() == 0) throw new ApiException(HttpStatus.BAD_REQUEST, "account.adjustment_zero");
        LocalDate date = req.date() == null ? time.today(userId) : req.date();
        if (date.isBefore(a.getOpeningDate())) date = a.getOpeningDate();
        return adjustments.save(new AccountAdjustment(userId, a.getId(), date, amount, blankToNull(req.notes()), clock.instant()));
    }

    @Transactional
    public void deleteAdjustment(UUID userId, UUID id) {
        adjustments.delete(adjustments.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "adjustment.not_found")));
    }

    /** Cuenta propia y activa (para registrar movimientos nuevos). */
    public Account usable(UUID userId, UUID id) {
        Account a = owned(userId, id);
        if (a.isArchived()) throw new ApiException(HttpStatus.BAD_REQUEST, "account.archived");
        return a;
    }

    public Account owned(UUID userId, UUID id) {
        return accounts.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "account.not_found"));
    }

    @Transactional(readOnly = true)
    public AccountResponse response(UUID userId, Account a) {
        return toResponse(a, ledger.balances(userId, null).get(a.getId()));
    }

    private static AccountResponse toResponse(Account a, BigDecimal balance) {
        return new AccountResponse(a.getId(), a.getName(), a.getKind(), a.getCurrency(), a.getColor(),
                a.getOpeningBalance(), a.getOpeningDate(), balance == null ? a.getOpeningBalance() : Money.of(balance),
                a.getKind().isSavings(), a.isArchived());
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
