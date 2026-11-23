package com.saver.income;

import com.saver.common.ApiException;
import com.saver.common.Money;
import com.saver.income.IncomeDtos.AmountChangeRequest;
import com.saver.income.IncomeDtos.AmountVersion;
import com.saver.income.IncomeDtos.CreateIncomeRequest;
import com.saver.income.IncomeDtos.IncomeResponse;
import com.saver.income.IncomeDtos.UpdateIncomeRequest;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class IncomeService {

    private final IncomeRepository incomes;
    private final IncomeReceiptRepository receipts;

    public IncomeService(IncomeRepository incomes, IncomeReceiptRepository receipts) {
        this.incomes = incomes;
        this.receipts = receipts;
    }

    @Transactional(readOnly = true)
    public List<Income> list(UUID userId) {
        return incomes.findAllWithAmounts(userId);
    }

    @Transactional
    public Income create(UUID userId, CreateIncomeRequest req) {
        YearMonth end = req.months() == null ? null : req.startMonth().plusMonths(req.months() - 1L);
        Income income = new Income(userId, req.description().trim(), req.kind(), req.currency(),
                req.startMonth(), end, Money.of(req.amount()));
        if (req.adjustEveryMonths() != null) {
            if (!income.isRecurring()) throw new ApiException(HttpStatus.BAD_REQUEST, "income.adjust_only_recurring");
            income.setAdjustment(req.adjustEveryMonths(), req.adjustPercent(), req.adjustWithInflation());
        }
        return incomes.save(income);
    }

    @Transactional
    public Income update(UUID userId, UUID id, UpdateIncomeRequest req) {
        Income income = owned(userId, id);
        if (req.description() != null) income.setDescription(req.description().trim());
        if (Boolean.TRUE.equals(req.clearEnd())) {
            income.setEndMonth(null);
        } else if (req.endMonth() != null) {
            if (req.endMonth().isBefore(income.getStartMonth())) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "income.end_before_start");
            }
            income.setEndMonth(req.endMonth());
        }
        if (Boolean.TRUE.equals(req.clearAdjustment())) {
            income.setAdjustment(null, null, false);
        } else if (req.adjustEveryMonths() != null || req.adjustPercent() != null || req.adjustWithInflation() != null) {
            if (!income.isRecurring()) throw new ApiException(HttpStatus.BAD_REQUEST, "income.adjust_only_recurring");
            income.setAdjustment(
                    req.adjustEveryMonths() != null ? req.adjustEveryMonths() : income.getAdjustEveryMonths(),
                    req.adjustPercent() != null ? req.adjustPercent() : income.getAdjustPercent(),
                    req.adjustWithInflation() != null ? req.adjustWithInflation() : income.isAdjustWithInflation());
        }
        return income;
    }

    /** Nuevo monto desde un mes (ej. aumento de sueldo). */
    @Transactional
    public Income changeAmount(UUID userId, UUID id, AmountChangeRequest req) {
        Income income = owned(userId, id);
        if (req.effectiveMonth().isBefore(income.getStartMonth())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "income.amount_before_start");
        }
        income.setAmountFrom(req.effectiveMonth(), Money.of(req.amount()));
        return income;
    }

    @Transactional
    public void delete(UUID userId, UUID id) {
        Income income = owned(userId, id);
        // Con cobros registrados se da de baja (fin) en lugar de borrarse: los saldos de las cuentas dependen de ellos.
        if (receipts.existsByIncomeId(income.getId())) throw new ApiException(HttpStatus.CONFLICT, "income.has_receipts");
        incomes.delete(income);
    }

    public static IncomeResponse toResponse(Income i, YearMonth current) {
        List<AmountVersion> versions = i.getAmounts().stream()
                .map(a -> new AmountVersion(a.getEffectiveMonth(), a.getAmount())).toList();
        YearMonth ref = current.isBefore(i.getStartMonth()) ? i.getStartMonth() : current;
        return new IncomeResponse(i.getId(), i.getDescription(), i.getKind(), i.getCurrency(),
                i.getStartMonth(), i.getEndMonth(), i.isRecurring(),
                i.amountFor(ref).orElse(null), versions,
                i.getAdjustEveryMonths(), i.getAdjustPercent(), i.isAdjustWithInflation(), i.nextAdjustment(current));
    }

    private Income owned(UUID userId, UUID id) {
        return incomes.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "income.not_found"));
    }
}
