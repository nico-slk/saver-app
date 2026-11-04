package com.saver.history;

import com.saver.common.ApiException;
import com.saver.common.Money;
import com.saver.common.Months;
import com.saver.expense.Expense;
import com.saver.expense.ExpenseOccurrence;
import com.saver.expense.OccurrenceService;
import com.saver.history.HistoryDtos.CategoryAmount;
import com.saver.history.HistoryDtos.CategoryChange;
import com.saver.history.HistoryDtos.Change;
import com.saver.history.HistoryDtos.CompareResponse;
import com.saver.history.HistoryDtos.ExpenseChange;
import com.saver.history.HistoryDtos.HistoryResponse;
import com.saver.history.HistoryDtos.MonthPoint;
import com.saver.history.HistoryDtos.Summary;
import com.saver.income.Income;
import com.saver.income.IncomeService;
import com.saver.inflation.InflationCurve;
import com.saver.inflation.InflationService;
import com.saver.user.UserTime;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Historial real de meses pasados (y el actual): ingresos vigentes y gastos generados en cada mes.
 * El ahorro de un mes es ingresos − gastos (cuando exista el cierre de mes se usará su snapshot).
 */
@Service
public class HistoryService {

    static final int MAX_MONTHS = 120;
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private final IncomeService incomes;
    private final OccurrenceService occurrences;
    private final InflationService inflation;
    private final UserTime time;

    public HistoryService(IncomeService incomes, OccurrenceService occurrences, InflationService inflation, UserTime time) {
        this.incomes = incomes;
        this.occurrences = occurrences;
        this.inflation = inflation;
        this.time = time;
    }

    @Transactional
    public HistoryResponse history(UUID userId, YearMonth from, YearMonth to, String currency, Set<UUID> categories) {
        YearMonth current = time.currentMonth(userId);
        to = to == null || to.isAfter(current) ? current : to;
        from = from == null ? to.minusMonths(11) : from;
        validateRange(from, to);
        occurrences.ensureGenerated(userId, current);

        // Se incluye el mes anterior a "from" para calcular la variación del primer mes.
        Data data = load(userId, from.minusMonths(1), to, currency);
        InflationCurve curve = inflation.curve(userId, currency);
        List<MonthPoint> points = new ArrayList<>();
        for (YearMonth m = from; !m.isAfter(to); m = m.plusMonths(1)) {
            points.add(withInflation(point(data, m, categories), curve, to));
        }

        BigDecimal totalIncome = sum(points.stream().map(MonthPoint::income).toList());
        BigDecimal totalExpenses = sum(points.stream().map(MonthPoint::expenses).toList());
        BigDecimal totalSavings = sum(points.stream().map(MonthPoint::savings).toList());
        MonthPoint last = points.getLast();
        MonthPoint previous = point(data, to.minusMonths(1), categories);
        Change expensesChange = change(points.getFirst().expenses(), last.expenses());
        BigDecimal rangeInflation = curve.accumulated(from.plusMonths(1), to);
        BigDecimal totalSavingsReal = points.stream().map(p -> p.savings().multiply(p.realFactor()))
                .reduce(BigDecimal.ZERO, BigDecimal::add).setScale(2, RoundingMode.HALF_UP);
        Summary summary = new Summary(
                change(previous.savings(), last.savings()),
                average(totalSavings, points.size()),
                totalIncome, totalExpenses, totalSavings,
                expensesChange,
                InflationService.percent(rangeInflation).setScale(2, RoundingMode.HALF_UP),
                estimated(curve, from, to), totalSavingsReal,
                realPercent(expensesChange.percent(), rangeInflation));
        return new HistoryResponse(from, to, currency, List.copyOf(categories), points, summary);
    }

    /** Compara dos meses: qué gastos subieron o bajaron, por gasto y por categoría, y el ahorro. */
    @Transactional
    public CompareResponse compare(UUID userId, YearMonth from, YearMonth to, String currency) {
        YearMonth current = time.currentMonth(userId);
        to = to == null || to.isAfter(current) ? current : to;
        validateRange(from, to);
        occurrences.ensureGenerated(userId, current);
        Data data = load(userId, from, to, currency);
        InflationCurve curve = inflation.curve(userId, currency);
        BigDecimal rangeInflation = curve.accumulated(from.plusMonths(1), to);

        Map<UUID, ExpenseOccurrence> fromItems = byExpense(data.occurrences(from));
        Map<UUID, ExpenseOccurrence> toItems = byExpense(data.occurrences(to));
        Set<UUID> ids = new LinkedHashSet<>(fromItems.keySet());
        ids.addAll(toItems.keySet());

        List<ExpenseChange> items = new ArrayList<>();
        for (UUID id : ids) {
            ExpenseOccurrence a = fromItems.get(id);
            ExpenseOccurrence b = toItems.get(id);
            Expense e = (b != null ? b : a).getExpense();
            BigDecimal av = a == null ? null : OccurrenceService.effectiveAmount(a);
            BigDecimal bv = b == null ? null : OccurrenceService.effectiveAmount(b);
            String status = a == null ? "NEW" : b == null ? "ENDED" : bv.compareTo(av) > 0 ? "UP" : bv.compareTo(av) < 0 ? "DOWN" : "SAME";
            BigDecimal diff = Money.nz(bv).subtract(Money.nz(av));
            BigDecimal pct = a == null ? null : percent(av, bv);
            items.add(new ExpenseChange(id, e.getDescription(), e.getCategoryId(), e.getKind(), av, bv, diff,
                    pct, status, b == null ? null : realPercent(pct, rangeInflation)));
        }
        items.sort(Comparator.comparing((ExpenseChange c) -> order(c.status()))
                .thenComparing(c -> c.percent() == null ? BigDecimal.ZERO : c.percent(), Comparator.reverseOrder())
                .thenComparing(ExpenseChange::description));

        Map<UUID, BigDecimal> catFrom = byCategory(data.occurrences(from), null);
        Map<UUID, BigDecimal> catTo = byCategory(data.occurrences(to), null);
        Set<UUID> cats = new LinkedHashSet<>(catFrom.keySet());
        cats.addAll(catTo.keySet());
        List<CategoryChange> categories = cats.stream()
                .map(c -> new CategoryChange(c, change(Money.nz(catFrom.get(c)), Money.nz(catTo.get(c)))))
                .sorted(Comparator.comparing((CategoryChange c) -> c.change().diff()).reversed())
                .toList();

        MonthPoint pf = point(data, from, Set.of());
        MonthPoint pt = point(data, to, Set.of());
        int count = (int) Months.between(from, to) + 1;
        BigDecimal savingsSum = BigDecimal.ZERO;
        for (YearMonth m = from; !m.isAfter(to); m = m.plusMonths(1)) savingsSum = savingsSum.add(point(data, m, Set.of()).savings());

        Change expensesChange = change(pf.expensesTotal(), pt.expensesTotal());
        BigDecimal fromSavingsReal = pf.savings().multiply(BigDecimal.ONE.add(rangeInflation)).setScale(2, RoundingMode.HALF_UP);
        return new CompareResponse(from, to, currency,
                change(pf.income(), pt.income()), expensesChange,
                change(pf.savings(), pt.savings()), average(savingsSum, count), count, items, categories,
                InflationService.percent(rangeInflation).setScale(2, RoundingMode.HALF_UP), estimated(curve, from, to),
                change(fromSavingsReal, pt.savings()), realPercent(expensesChange.percent(), rangeInflation));
    }

    // ── cálculo ──────────────────────────────────────────────────────────────

    private record Data(List<Income> incomes, Map<YearMonth, List<ExpenseOccurrence>> byMonth, String currency) {
        List<ExpenseOccurrence> occurrences(YearMonth m) {
            return byMonth.getOrDefault(m, List.of());
        }
    }

    private Data load(UUID userId, YearMonth from, YearMonth to, String currency) {
        Map<YearMonth, List<ExpenseOccurrence>> byMonth = new HashMap<>();
        for (ExpenseOccurrence o : occurrences.range(userId, from, to)) {
            if (currency.equals(o.getCurrency())) byMonth.computeIfAbsent(o.getMonth(), k -> new ArrayList<>()).add(o);
        }
        List<Income> inc = incomes.list(userId).stream().filter(i -> currency.equals(i.getCurrency())).toList();
        return new Data(inc, byMonth, currency);
    }

    private static MonthPoint point(Data data, YearMonth m, Set<UUID> categories) {
        BigDecimal income = data.incomes().stream().filter(i -> i.activeIn(m))
                .map(i -> i.amountFor(m).orElse(BigDecimal.ZERO)).reduce(Money.ZERO, BigDecimal::add);
        List<ExpenseOccurrence> occ = data.occurrences(m);
        BigDecimal total = sum(occ.stream().map(OccurrenceService::effectiveAmount).toList());
        Map<UUID, BigDecimal> cats = byCategory(occ, categories);
        BigDecimal filtered = categories.isEmpty() ? total : sum(cats.values());
        List<CategoryAmount> byCategory = cats.entrySet().stream()
                .map(e -> new CategoryAmount(e.getKey(), e.getValue()))
                .sorted(Comparator.comparing(CategoryAmount::amount).reversed()).toList();
        return new MonthPoint(m, income, filtered, total, income.subtract(total), byCategory, null, false, BigDecimal.ONE);
    }

    private static MonthPoint withInflation(MonthPoint p, InflationCurve curve, YearMonth to) {
        BigDecimal factor = curve.factor(p.month().plusMonths(1), to).setScale(6, RoundingMode.HALF_UP);
        return new MonthPoint(p.month(), p.income(), p.expenses(), p.expensesTotal(), p.savings(), p.byCategory(),
                InflationService.percent(curve.rate(p.month())), curve.isOfficial(p.month()), factor);
    }

    /** true si algún mes de (from, to] no tiene dato oficial. */
    private static boolean estimated(InflationCurve curve, YearMonth from, YearMonth to) {
        for (YearMonth m = from.plusMonths(1); !m.isAfter(to); m = m.plusMonths(1)) {
            if (!curve.isOfficial(m)) return true;
        }
        return false;
    }

    /**
     * Variación real en %: (1 + nominal) / (1 + inflación) − 1. Positivo = subió más que la inflación.
     * Null si no hay variación nominal.
     */
    static BigDecimal realPercent(BigDecimal nominalPercent, BigDecimal inflationFraction) {
        if (nominalPercent == null) return null;
        BigDecimal nominal = BigDecimal.ONE.add(nominalPercent.movePointLeft(2));
        return nominal.divide(BigDecimal.ONE.add(inflationFraction), java.math.MathContext.DECIMAL64)
                .subtract(BigDecimal.ONE).movePointRight(2).setScale(2, RoundingMode.HALF_UP);
    }

    private static Map<UUID, BigDecimal> byCategory(List<ExpenseOccurrence> occ, Set<UUID> filter) {
        Map<UUID, BigDecimal> map = new LinkedHashMap<>();
        for (ExpenseOccurrence o : occ) {
            UUID c = o.getExpense().getCategoryId();
            if (filter != null && !filter.isEmpty() && !filter.contains(c)) continue;
            map.merge(c, OccurrenceService.effectiveAmount(o), BigDecimal::add);
        }
        return map;
    }

    private static Map<UUID, ExpenseOccurrence> byExpense(List<ExpenseOccurrence> occ) {
        Map<UUID, ExpenseOccurrence> map = new LinkedHashMap<>();
        occ.forEach(o -> map.put(o.getExpense().getId(), o));
        return map;
    }

    static Change change(BigDecimal from, BigDecimal to) {
        return new Change(from, to, to.subtract(from), percent(from, to));
    }

    /** Variación porcentual (2 decimales). Null si el valor de partida es 0. */
    static BigDecimal percent(BigDecimal from, BigDecimal to) {
        if (from == null || to == null || from.signum() == 0) return null;
        return to.subtract(from).multiply(HUNDRED).divide(from.abs(), 2, RoundingMode.HALF_UP);
    }

    private static BigDecimal average(BigDecimal total, int count) {
        return count == 0 ? Money.ZERO : total.divide(BigDecimal.valueOf(count), 2, RoundingMode.HALF_UP);
    }

    private static BigDecimal sum(Collection<BigDecimal> values) {
        return values.stream().reduce(Money.ZERO, BigDecimal::add);
    }

    private static int order(String status) {
        return switch (status) {
            case "UP" -> 0;
            case "DOWN" -> 1;
            case "SAME" -> 2;
            case "NEW" -> 3;
            default -> 4;
        };
    }

    private static void validateRange(YearMonth from, YearMonth to) {
        if (from == null || from.isAfter(to)) throw new ApiException(HttpStatus.BAD_REQUEST, "history.invalid_range");
        if (Months.between(from, to) >= MAX_MONTHS) throw new ApiException(HttpStatus.BAD_REQUEST, "history.range_too_long");
    }
}
