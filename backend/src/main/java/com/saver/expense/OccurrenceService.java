package com.saver.expense;

import com.saver.common.ApiException;
import com.saver.common.Money;
import com.saver.expense.ExpenseDtos.OccurrenceResponse;
import com.saver.expense.ExpenseDtos.UpdateOccurrenceRequest;
import com.saver.inflation.InflationService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Genera las ocurrencias mensuales a partir de las plantillas (hasta el mes actual) y calcula
 * las de meses futuros sin guardarlas.
 */
@Service
public class OccurrenceService {

    private final ExpenseRepository expenses;
    private final OccurrenceRepository occurrences;

    public OccurrenceService(ExpenseRepository expenses, OccurrenceRepository occurrences) {
        this.expenses = expenses;
        this.occurrences = occurrences;
    }

    /** Crea las ocurrencias faltantes de todas las plantillas hasta {@code upTo} inclusive. */
    @Transactional
    public void ensureGenerated(UUID userId, YearMonth upTo) {
        Set<OccurrenceKey> existing = new HashSet<>(occurrences.findKeys(userId));
        for (Expense e : expenses.findAllWithAmounts(userId)) {
            YearMonth last = e.getEndMonth() == null || e.getEndMonth().isAfter(upTo) ? upTo : e.getEndMonth();
            for (YearMonth m = e.getStartMonth(); !m.isAfter(last); m = m.plusMonths(1)) {
                if (existing.contains(new OccurrenceKey(e.getId(), m))) continue;
                occurrences.insertIfAbsent(UUID.randomUUID(), userId, e.getId(), m.atDay(1),
                        ExpenseSchedule.installmentNumber(e, m), ExpenseSchedule.dueDate(m, e.getDueDay()),
                        e.amountFor(m), e.getCurrency());
            }
        }
    }

    @Transactional(readOnly = true)
    public List<ExpenseOccurrence> month(UUID userId, YearMonth month) {
        return occurrences.findMonth(userId, month);
    }

    @Transactional(readOnly = true)
    public List<ExpenseOccurrence> range(UUID userId, YearMonth from, YearMonth to) {
        return occurrences.findRange(userId, from, to);
    }

    /** Monto que cuenta para historial y promedios: lo pagado si está pago, si no lo previsto. */
    public static BigDecimal effectiveAmount(ExpenseOccurrence o) {
        return o.getStatus() == OccurrenceStatus.PAID && o.getPaidAmount().signum() > 0
                ? o.getPaidAmount() : o.getExpectedAmount();
    }

    @Transactional(readOnly = true)
    public List<ExpenseOccurrence> unpaidBefore(UUID userId, YearMonth month) {
        return occurrences.findUnpaidBefore(userId, month, OccurrenceStatus.PAID);
    }

    /** Meses que se promedian para estimar los gastos de monto variable (luz, gas). */
    public static final int VARIABLE_AVERAGE_MONTHS = 3;

    /**
     * Promedio de los últimos {@value #VARIABLE_AVERAGE_MONTHS} meses registrados (hasta {@code current})
     * de cada gasto de monto variable, por id de gasto.
     */
    @Transactional(readOnly = true)
    public Map<UUID, BigDecimal> variableAverages(UUID userId, YearMonth current) {
        Map<UUID, List<BigDecimal>> values = new HashMap<>();
        for (ExpenseOccurrence o : occurrences.findRange(userId, current.minusMonths(VARIABLE_AVERAGE_MONTHS - 1L), current)) {
            if (o.getExpense().isVariableAmount()) {
                values.computeIfAbsent(o.getExpense().getId(), k -> new ArrayList<>()).add(effectiveAmount(o));
            }
        }
        Map<UUID, BigDecimal> result = new HashMap<>();
        values.forEach((id, list) -> result.put(id, list.stream().reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(list.size()), 2, RoundingMode.HALF_UP)));
        return result;
    }

    /**
     * Ocurrencias calculadas (no guardadas) de un mes futuro: aumentos estimados (con la inflación de
     * la moneda de cada gasto) y, para montos variables, el promedio de los últimos meses.
     */
    @Transactional(readOnly = true)
    public List<OccurrenceResponse> projected(UUID userId, YearMonth month, LocalDate today, InflationService.Curves curves) {
        List<OccurrenceResponse> result = new ArrayList<>();
        Map<UUID, BigDecimal> averages = variableAverages(userId, YearMonth.from(today));
        for (Expense e : expenses.findAllWithAmounts(userId)) {
            if (!e.activeIn(month)) continue;
            LocalDate due = ExpenseSchedule.dueDate(month, e.getDueDay());
            result.add(new OccurrenceResponse(null, e.getId(), e.getDescription(), e.getCategoryId(), e.getKind(),
                    month, due, ChronoUnit.DAYS.between(today, due),
                    ExpenseSchedule.installmentNumber(e, month), e.getInstallments(),
                    e.isVariableAmount() && averages.containsKey(e.getId()) ? averages.get(e.getId())
                            : ExpenseSchedule.estimatedAmount(e, month, curves.of(e.getCurrency())), Money.ZERO, e.getCurrency(),
                    OccurrenceStatus.PENDING.name(), e.isVariableAmount(), true, null, e.getCardId()));
        }
        result.sort(Comparator.comparing(OccurrenceResponse::dueDate).thenComparing(OccurrenceResponse::description));
        return result;
    }

    @Transactional
    public ExpenseOccurrence update(UUID userId, UUID id, UpdateOccurrenceRequest req) {
        ExpenseOccurrence o = occurrences.findOwned(id, userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "occurrence.not_found"));
        if (req.dueDate() != null) o.setDueDate(req.dueDate());
        if (req.expectedAmount() != null) {
            o.setExpectedAmount(Money.of(req.expectedAmount()));
            o.applyPaid(o.getPaidAmount());
        }
        if (req.notes() != null) o.setNotes(req.notes().isBlank() ? null : req.notes().trim());
        return o;
    }

    /** Tras un cambio de monto: recalcula las ocurrencias sin pagos desde {@code from}. */
    void refreshAmounts(Expense e, YearMonth from) {
        for (ExpenseOccurrence o : occurrences.findFrom(e.getId(), from)) {
            if (o.isUntouched()) o.setExpectedAmount(e.amountFor(o.getMonth()));
        }
    }

    /** Tras cambiar el día de vencimiento: actualiza las ocurrencias sin pagos desde {@code from}. */
    void refreshDueDates(Expense e, YearMonth from) {
        for (ExpenseOccurrence o : occurrences.findFrom(e.getId(), from)) {
            if (o.isUntouched()) o.setDueDate(ExpenseSchedule.dueDate(o.getMonth(), e.getDueDay()));
        }
    }

    /** Tras dar de baja un gasto: borra las ocurrencias sin pagos posteriores al fin. */
    void removeAfter(Expense e, YearMonth end) {
        occurrences.findAfter(e.getId(), end).stream().filter(ExpenseOccurrence::isUntouched).forEach(occurrences::delete);
    }

    public static OccurrenceResponse toResponse(ExpenseOccurrence o, LocalDate today) {
        Expense e = o.getExpense();
        boolean overdue = o.getStatus() != OccurrenceStatus.PAID && o.getDueDate().isBefore(today);
        return new OccurrenceResponse(o.getId(), e.getId(), e.getDescription(), e.getCategoryId(), e.getKind(),
                o.getMonth(), o.getDueDate(), ChronoUnit.DAYS.between(today, o.getDueDate()),
                o.getInstallmentNumber(), e.getInstallments(), o.getExpectedAmount(), o.getPaidAmount(),
                o.getCurrency(), overdue ? "OVERDUE" : o.getStatus().name(), e.isVariableAmount(), false, o.getNotes(),
                e.getCardId());
    }
}
