package com.saver.payment;

import com.saver.account.Account;
import com.saver.account.AccountRepository;
import com.saver.account.AccountService;
import com.saver.common.ApiException;
import com.saver.common.Money;
import com.saver.exchange.ExchangeRateService;
import com.saver.exchange.ExchangeRateService.Conversion;
import com.saver.exchange.ExchangeRateService.Purpose;
import com.saver.expense.Expense;
import com.saver.expense.ExpenseOccurrence;
import com.saver.expense.OccurrenceRepository;
import com.saver.payment.PaymentDtos.AttachmentResponse;
import com.saver.payment.PaymentDtos.OccurrencePayments;
import com.saver.payment.PaymentDtos.PaymentRequest;
import com.saver.payment.PaymentDtos.PaymentResponse;
import com.saver.user.UserTime;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Pagos de gastos. El estado de la ocurrencia (PENDIENTE / PARCIAL / PAGADO) se recalcula con la suma de sus pagos.
 */
@Service
public class PaymentService {

    private final PaymentRepository payments;
    private final PaymentAttachmentRepository attachments;
    private final AttachmentService attachmentService;
    private final OccurrenceRepository occurrences;
    private final AccountService accountService;
    private final AccountRepository accounts;
    private final ExchangeRateService rates;
    private final UserTime time;
    private final Clock clock;

    public PaymentService(PaymentRepository payments, PaymentAttachmentRepository attachments,
                          AttachmentService attachmentService, OccurrenceRepository occurrences,
                          AccountService accountService, AccountRepository accounts, ExchangeRateService rates,
                          UserTime time, Clock clock) {
        this.payments = payments;
        this.attachments = attachments;
        this.attachmentService = attachmentService;
        this.occurrences = occurrences;
        this.accountService = accountService;
        this.accounts = accounts;
        this.rates = rates;
        this.time = time;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public OccurrencePayments list(UUID userId, UUID occurrenceId) {
        return view(occurrence(userId, occurrenceId));
    }

    @Transactional
    public OccurrencePayments pay(UUID userId, UUID occurrenceId, PaymentRequest req) {
        ExpenseOccurrence o = occurrence(userId, occurrenceId);
        Account account = accountService.usable(userId, req.accountId());
        BigDecimal amount = Money.of(req.amount());
        Purpose purpose = o.getExpense().getCardId() != null ? Purpose.PAYMENT_CARD : Purpose.PAYMENT;
        Conversion c = rates.convert(amount, o.getCurrency(), account.getCurrency(), purpose, req.rate());
        LocalDate date = req.date() == null ? time.today(userId) : req.date();
        payments.save(new Payment(userId, o.getId(), account.getId(), date, amount, c.amount(), c.rate(), c.type(),
                blankToNull(req.notes()), false, clock.instant()));
        payments.flush();
        o.applyPaid(payments.totalFor(o.getId()));
        return view(o);
    }

    @Transactional
    public OccurrencePayments delete(UUID userId, UUID paymentId) {
        Payment p = payments.findByIdAndUserId(paymentId, userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "payment.not_found"));
        attachmentService.deleteFilesOf(attachments.findByPaymentIdIn(List.of(p.getId())));
        payments.delete(p);
        payments.flush();
        ExpenseOccurrence o = occurrence(userId, p.getOccurrenceId());
        o.applyPaid(payments.totalFor(o.getId()));
        return view(o);
    }

    /**
     * Marca como pagados los meses anteriores a {@code current} de un gasto recién creado: un pago
     * "asumido" por lo que falta, fechado en el vencimiento, sin cuenta (no mueve saldos).
     */
    @Transactional
    public void assumePaidBefore(UUID userId, Expense e, YearMonth current) {
        for (ExpenseOccurrence o : occurrences.findBefore(e.getId(), current)) {
            BigDecimal missing = o.getExpectedAmount().subtract(o.getPaidAmount());
            if (missing.signum() <= 0) continue;
            payments.save(new Payment(userId, o.getId(), null, o.getDueDate(), missing, null, null, null, null, true,
                    clock.instant()));
            o.applyPaid(o.getPaidAmount().add(missing));
        }
    }

    @Transactional(readOnly = true)
    public boolean hasRealPayments(UUID expenseId) {
        return payments.existsRealForExpense(expenseId);
    }

    private OccurrencePayments view(ExpenseOccurrence o) {
        List<Payment> list = payments.findByOccurrenceIdOrderByPaidOnAscCreatedAtAsc(o.getId());
        Map<UUID, List<AttachmentResponse>> files = new HashMap<>();
        attachments.findByPaymentIdIn(list.stream().map(Payment::getId).toList())
                .forEach(a -> files.computeIfAbsent(a.getPaymentId(), k -> new ArrayList<>()).add(AttachmentService.toResponse(a)));
        Map<UUID, Account> accs = new HashMap<>();
        accounts.findAllById(list.stream().map(Payment::getAccountId).filter(java.util.Objects::nonNull).toList())
                .forEach(a -> accs.put(a.getId(), a));
        List<PaymentResponse> items = list.stream().map(p -> {
            Account a = p.getAccountId() == null ? null : accs.get(p.getAccountId());
            return new PaymentResponse(p.getId(), o.getId(), p.getAccountId(), a == null ? null : a.getName(),
                    a == null ? null : a.getCurrency(), p.getPaidOn(), p.getAmount(), o.getCurrency(),
                    p.getAccountAmount(), p.getRate(), p.getRateType(), p.getNotes(), p.isAssumed(),
                    files.getOrDefault(p.getId(), List.of()));
        }).toList();
        return new OccurrencePayments(o.getId(), o.getStatus().name(), o.getExpectedAmount(), o.getPaidAmount(),
                o.getExpectedAmount().subtract(o.getPaidAmount()).max(BigDecimal.ZERO), o.getCurrency(),
                o.getExpense().getCardId(), items);
    }

    private ExpenseOccurrence occurrence(UUID userId, UUID id) {
        return occurrences.findOwned(id, userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "occurrence.not_found"));
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
