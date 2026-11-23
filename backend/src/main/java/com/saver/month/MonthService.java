package com.saver.month;

import com.saver.common.Money;
import com.saver.expense.ExpenseDtos.OccurrenceResponse;
import com.saver.expense.OccurrenceService;
import com.saver.income.Income;
import com.saver.income.IncomeReceiptService;
import com.saver.income.IncomeService;
import com.saver.month.MonthDtos.CategoryTotal;
import com.saver.month.MonthDtos.CurrencyTotals;
import com.saver.month.MonthDtos.MonthIncome;
import com.saver.month.MonthDtos.MonthResponse;
import com.saver.inflation.InflationService;
import com.saver.user.UserTime;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Vista de un mes: ingresos, gastos a pagar, vencidos y balance. */
@Service
public class MonthService {

    private final IncomeService incomes;
    private final OccurrenceService occurrences;
    private final UserTime time;
    private final InflationService inflation;
    private final IncomeReceiptService receipts;

    public MonthService(IncomeService incomes, OccurrenceService occurrences, UserTime time, InflationService inflation,
                        IncomeReceiptService receipts) {
        this.receipts = receipts;
        this.incomes = incomes;
        this.occurrences = occurrences;
        this.time = time;
        this.inflation = inflation;
    }

    @Transactional
    public MonthResponse get(UUID userId, YearMonth month) {
        LocalDate today = time.today(userId);
        YearMonth current = YearMonth.from(today);
        boolean projected = month.isAfter(current);

        InflationService.Curves curves = inflation.curves(userId);
        List<OccurrenceResponse> items;
        List<OccurrenceResponse> overdue = List.of();
        if (projected) {
            items = occurrences.projected(userId, month, today, curves);
        } else {
            occurrences.ensureGenerated(userId, current);
            items = occurrences.month(userId, month).stream().map(o -> OccurrenceService.toResponse(o, today)).toList();
            if (month.equals(current)) {
                overdue = occurrences.unpaidBefore(userId, month).stream()
                        .map(o -> OccurrenceService.toResponse(o, today)).toList();
            }
        }

        Map<UUID, BigDecimal> received = projected ? Map.of() : receipts.receivedIn(userId, month);
        List<MonthIncome> monthIncomes = new ArrayList<>();
        for (Income i : incomes.list(userId)) {
            if (!i.activeIn(month)) continue;
            (projected ? i.estimatedAmount(month, curves.of(i.getCurrency())) : i.amountFor(month)).ifPresent(amount -> monthIncomes.add(new MonthIncome(i.getId(), i.getDescription(),
                    i.getKind(), i.getCurrency(), amount, i.isRecurring(),
                    received.getOrDefault(i.getId(), Money.ZERO))));
        }
        monthIncomes.sort(Comparator.comparing(MonthIncome::kind).thenComparing(MonthIncome::description));

        return new MonthResponse(month, current, projected, totals(monthIncomes, items, overdue),
                byCategory(items), monthIncomes, items, overdue);
    }

    private static List<CurrencyTotals> totals(List<MonthIncome> inc, List<OccurrenceResponse> items,
                                               List<OccurrenceResponse> overdue) {
        TreeSet<String> currencies = new TreeSet<>();
        inc.forEach(i -> currencies.add(i.currency()));
        items.forEach(o -> currencies.add(o.currency()));
        overdue.forEach(o -> currencies.add(o.currency()));
        List<CurrencyTotals> result = new ArrayList<>();
        for (String c : currencies) {
            BigDecimal income = inc.stream().filter(i -> c.equals(i.currency())).map(MonthIncome::amount)
                    .reduce(Money.ZERO, BigDecimal::add);
            BigDecimal expenses = sum(items, c, false);
            BigDecimal paid = sum(items, c, true);
            BigDecimal overdueAmount = overdue.stream().filter(o -> c.equals(o.currency()))
                    .map(o -> o.expectedAmount().subtract(o.paidAmount()).max(BigDecimal.ZERO))
                    .reduce(Money.ZERO, BigDecimal::add);
            result.add(new CurrencyTotals(c, income, expenses, paid, expenses.subtract(paid).max(Money.ZERO),
                    overdueAmount, income.subtract(expenses)));
        }
        return result;
    }

    private static BigDecimal sum(List<OccurrenceResponse> items, String currency, boolean paid) {
        return items.stream().filter(o -> currency.equals(o.currency()))
                .map(o -> paid ? o.paidAmount() : o.expectedAmount())
                .reduce(Money.ZERO, BigDecimal::add);
    }

    private static List<CategoryTotal> byCategory(List<OccurrenceResponse> items) {
        Map<String, CategoryTotal> map = new LinkedHashMap<>();
        for (OccurrenceResponse o : items) {
            String key = o.categoryId() + "|" + o.currency();
            CategoryTotal prev = map.get(key);
            BigDecimal amount = prev == null ? o.expectedAmount() : prev.amount().add(o.expectedAmount());
            map.put(key, new CategoryTotal(o.categoryId(), o.currency(), amount));
        }
        List<CategoryTotal> result = new ArrayList<>(map.values());
        result.sort(Comparator.comparing(CategoryTotal::amount).reversed());
        return result;
    }
}
