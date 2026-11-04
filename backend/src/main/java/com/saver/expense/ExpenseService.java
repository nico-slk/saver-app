package com.saver.expense;

import com.saver.category.CategoryService;
import com.saver.common.ApiException;
import com.saver.common.Money;
import com.saver.common.Months;
import com.saver.expense.ExpenseDtos.AmountChangeRequest;
import com.saver.expense.ExpenseDtos.AmountVersion;
import com.saver.expense.ExpenseDtos.CreateExpenseRequest;
import com.saver.expense.ExpenseDtos.ExpenseResponse;
import com.saver.expense.ExpenseDtos.UpdateExpenseRequest;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ExpenseService {

    private final ExpenseRepository expenses;
    private final OccurrenceService occurrences;
    private final CategoryService categories;

    public ExpenseService(ExpenseRepository expenses, OccurrenceService occurrences, CategoryService categories) {
        this.expenses = expenses;
        this.occurrences = occurrences;
        this.categories = categories;
    }

    @Transactional(readOnly = true)
    public List<Expense> list(UUID userId) {
        return expenses.findAllWithAmounts(userId);
    }

    @Transactional
    public Expense create(UUID userId, CreateExpenseRequest req) {
        categories.requireUsable(userId, req.categoryId());
        if (req.kind() == ExpenseKind.INSTALLMENTS && req.installments() == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "expense.installments_required");
        }
        Expense e = new Expense(userId, req.categoryId(), req.description().trim(), req.kind(), req.currency(),
                req.startMonth(), req.installments(), null, Money.of(req.amount()));
        e.setDueDay(req.dueDay());
        e.setVariableAmount(req.variableAmount());
        if (req.kind() == ExpenseKind.RECURRING && req.adjustEveryMonths() != null) {
            e.setAdjustment(req.adjustEveryMonths(), req.adjustPercent(), req.adjustWithInflation());
        }
        e.setNotes(blankToNull(req.notes()));
        return expenses.save(e);
    }

    /** {@code current}: mes actual del usuario; los cambios de vencimiento aplican desde ahí. */
    @Transactional
    public Expense update(UUID userId, UUID id, UpdateExpenseRequest req, YearMonth current) {
        Expense e = owned(userId, id);
        if (req.description() != null) e.setDescription(req.description().trim());
        if (req.categoryId() != null && !req.categoryId().equals(e.getCategoryId())) {
            categories.requireUsable(userId, req.categoryId());
            e.setCategoryId(req.categoryId());
        }
        if (req.dueDay() != null) {
            e.setDueDay(req.dueDay() == 0 ? null : req.dueDay());
            occurrences.refreshDueDates(e, current);
        }
        if (req.variableAmount() != null) e.setVariableAmount(req.variableAmount());
        if (Boolean.TRUE.equals(req.clearAdjustment())) {
            e.setAdjustment(null, null, false);
        } else if (req.adjustEveryMonths() != null || req.adjustPercent() != null || req.adjustWithInflation() != null) {
            if (e.getKind() != ExpenseKind.RECURRING) throw new ApiException(HttpStatus.BAD_REQUEST, "expense.adjust_only_recurring");
            e.setAdjustment(
                    req.adjustEveryMonths() != null ? req.adjustEveryMonths() : e.getAdjustEveryMonths(),
                    req.adjustPercent() != null ? req.adjustPercent() : e.getAdjustPercent(),
                    req.adjustWithInflation() != null ? req.adjustWithInflation() : e.isAdjustWithInflation());
        }
        if (Boolean.TRUE.equals(req.clearEnd()) || req.endMonth() != null) {
            if (e.getKind() != ExpenseKind.RECURRING) throw new ApiException(HttpStatus.BAD_REQUEST, "expense.end_only_recurring");
            if (Boolean.TRUE.equals(req.clearEnd())) {
                e.setEndMonth(null);
            } else {
                if (req.endMonth().isBefore(e.getStartMonth())) throw new ApiException(HttpStatus.BAD_REQUEST, "expense.end_before_start");
                e.setEndMonth(req.endMonth());
                occurrences.removeAfter(e, req.endMonth());
            }
        }
        if (req.notes() != null) e.setNotes(blankToNull(req.notes()));
        return e;
    }

    /** Nuevo monto desde un mes (ej. aumento del alquiler). Recalcula las ocurrencias sin pagos. */
    @Transactional
    public Expense changeAmount(UUID userId, UUID id, AmountChangeRequest req) {
        Expense e = owned(userId, id);
        if (req.effectiveMonth().isBefore(e.getStartMonth())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "expense.amount_before_start");
        }
        e.setAmountFrom(req.effectiveMonth(), Money.of(req.amount()));
        occurrences.refreshAmounts(e, req.effectiveMonth());
        return e;
    }

    /** Borra la plantilla y sus ocurrencias. Si ya tiene pagos, hay que darla de baja (endMonth). */
    @Transactional
    public void delete(UUID userId, UUID id) {
        Expense e = owned(userId, id);
        if (occurrences.hasPayments(e)) throw new ApiException(HttpStatus.CONFLICT, "expense.has_payments");
        expenses.delete(e);
    }

    public static ExpenseResponse toResponse(Expense e, YearMonth current) {
        YearMonth ref = current.isBefore(e.getStartMonth()) ? e.getStartMonth() : current;
        List<AmountVersion> versions = e.getAmounts().stream()
                .map(a -> new AmountVersion(a.getEffectiveMonth(), a.getAmount())).toList();
        Integer remaining = null;
        BigDecimal total = null;
        if (e.getKind() == ExpenseKind.INSTALLMENTS) {
            remaining = current.isAfter(e.getEndMonth()) ? 0 : (int) Months.between(ref, e.getEndMonth()) + 1;
            total = e.amountFor(e.getStartMonth()).multiply(BigDecimal.valueOf(e.getInstallments()));
        }
        return new ExpenseResponse(e.getId(), e.getDescription(), e.getCategoryId(), e.getKind(), e.getCurrency(),
                e.getStartMonth(), e.getEndMonth(), e.getInstallments(), remaining, e.getDueDay(), e.isVariableAmount(),
                e.getAdjustEveryMonths(), e.getAdjustPercent(), e.isAdjustWithInflation(),
                ExpenseSchedule.nextAdjustment(e, current),
                e.amountFor(ref), total, versions, e.getNotes());
    }

    private Expense owned(UUID userId, UUID id) {
        return expenses.findOwned(id, userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "expense.not_found"));
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
