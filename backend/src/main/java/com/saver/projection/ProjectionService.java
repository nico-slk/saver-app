package com.saver.projection;

import com.saver.common.ApiException;
import com.saver.common.Money;
import com.saver.expense.Expense;
import com.saver.expense.ExpenseOccurrence;
import com.saver.expense.ExpenseRepository;
import com.saver.expense.ExpenseSchedule;
import com.saver.expense.OccurrenceService;
import com.saver.income.Income;
import com.saver.income.IncomeService;
import com.saver.inflation.InflationCurve;
import com.saver.inflation.InflationService;
import com.saver.projection.ProjectionDtos.ProjectionEvent;
import com.saver.projection.ProjectionDtos.ProjectionInflation;
import com.saver.projection.ProjectionDtos.ProjectionMonth;
import com.saver.projection.ProjectionDtos.ProjectionResponse;
import com.saver.projection.ProjectionDtos.ProjectionSummary;
import com.saver.user.UserTime;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Proyección de los próximos N meses a partir del mes actual (incluido), en una moneda.
 *
 * <ul>
 *   <li>Ingresos: el mes actual con el monto cargado; los futuros con los aumentos de su regla.</li>
 *   <li>Mes actual: los gastos generados (con sus ajustes). Meses futuros: plantillas con aumentos
 *       estimados; los de monto variable usan el promedio de los últimos 3 meses.</li>
 *   <li>Inflación: la de la moneda (oficial donde existe; para el resto la manual o la estimada).
 *       Se usa en los aumentos atados a inflación y para expresar el acumulado en valores de hoy.</li>
 * </ul>
 */
@Service
public class ProjectionService {

    static final int MAX_MONTHS = 120;

    private final IncomeService incomes;
    private final ExpenseRepository expenses;
    private final OccurrenceService occurrences;
    private final InflationService inflation;
    private final UserTime time;

    public ProjectionService(IncomeService incomes, ExpenseRepository expenses, OccurrenceService occurrences,
                             InflationService inflation, UserTime time) {
        this.incomes = incomes;
        this.expenses = expenses;
        this.occurrences = occurrences;
        this.inflation = inflation;
        this.time = time;
    }

    /**
     * @param screenInflation inflación mensual (fracción, 0.025 = 2,5 %) elegida en la pantalla;
     *                        null = la del perfil/estimada.
     */
    @Transactional
    public ProjectionResponse project(UUID userId, int months, String currency, BigDecimal startBalance,
                                      BigDecimal screenInflation) {
        if (months < 1 || months > MAX_MONTHS) throw new ApiException(HttpStatus.BAD_REQUEST, "projection.invalid_months");
        YearMonth current = time.currentMonth(userId);
        YearMonth last = current.plusMonths(months - 1L);
        occurrences.ensureGenerated(userId, current);

        InflationCurve base = inflation.curve(userId, currency);
        InflationCurve curve = screenInflation == null ? base : base.withManual(screenInflation);

        List<Income> allIncomes = incomes.list(userId);
        List<Expense> allExpenses = expenses.findAllWithAmounts(userId);
        List<Income> inc = allIncomes.stream().filter(i -> currency.equals(i.getCurrency())).toList();
        List<Expense> exp = allExpenses.stream().filter(e -> currency.equals(e.getCurrency())).toList();

        Map<UUID, ExpenseOccurrence> currentOcc = new HashMap<>();
        for (ExpenseOccurrence o : occurrences.month(userId, current)) currentOcc.put(o.getExpense().getId(), o);
        Map<UUID, BigDecimal> variableAverages = occurrences.variableAverages(userId, current);

        BigDecimal start = startBalance == null ? Money.ZERO : Money.of(startBalance);
        BigDecimal cumulative = start;
        BigDecimal cumulativeReal = start;
        List<ProjectionMonth> series = new ArrayList<>();
        YearMonth installmentsEnd = null;
        // Monto del mes anterior por ingreso/gasto: un aumento se marca cuando el monto sube
        // (incluye aumentos atrasados que no se registraron en su mes).
        Map<UUID, BigDecimal> previous = new HashMap<>();

        for (int k = 0; k < months; k++) {
            YearMonth m = current.plusMonths(k);
            List<ProjectionEvent> events = new ArrayList<>();

            BigDecimal income = Money.ZERO;
            for (Income i : inc) {
                if (!i.activeIn(m)) continue;
                BigDecimal amount = (k == 0 ? i.amountFor(m) : i.estimatedAmount(m, curve)).orElse(BigDecimal.ZERO);
                income = income.add(amount);
                if (increased(previous.put(i.getId(), amount), amount) && i.adjustmentRule() != null) {
                    events.add(new ProjectionEvent("INCOME_INCREASE", i.getId(), i.getDescription(), amount));
                }
                if (i.getEndMonth() != null && i.getEndMonth().equals(m) && i.isRecurring()) {
                    events.add(new ProjectionEvent("INCOME_ENDS", i.getId(), i.getDescription(), null));
                }
            }

            BigDecimal installments = Money.ZERO, recurring = Money.ZERO, oneTime = Money.ZERO;
            for (Expense e : exp) {
                if (!e.activeIn(m)) continue;
                BigDecimal amount = amountFor(e, m, current, currentOcc, variableAverages, curve);
                switch (e.getKind()) {
                    case INSTALLMENTS -> {
                        installments = installments.add(amount);
                        installmentsEnd = installmentsEnd == null || m.isAfter(installmentsEnd) ? m : installmentsEnd;
                        if (m.equals(e.getEndMonth())) {
                            events.add(new ProjectionEvent("INSTALLMENTS_END", e.getId(), e.getDescription(), amount));
                        }
                    }
                    case RECURRING -> {
                        recurring = recurring.add(amount);
                        if (increased(previous.put(e.getId(), amount), amount) && e.hasAdjustmentRule() && !e.isVariableAmount()) {
                            events.add(new ProjectionEvent("INCREASE", e.getId(), e.getDescription(), amount));
                        }
                    }
                    case ONE_TIME -> oneTime = oneTime.add(amount);
                }
            }

            BigDecimal expensesTotal = installments.add(recurring).add(oneTime);
            BigDecimal balance = income.subtract(expensesTotal);
            cumulative = cumulative.add(balance);
            // Ahorro del mes m expresado en moneda de hoy: se descuenta la inflación de los meses actual..m.
            cumulativeReal = cumulativeReal.add(balance.divide(curve.factor(current, m), 2, RoundingMode.HALF_UP));
            series.add(new ProjectionMonth(m, income, expensesTotal, installments, recurring, oneTime,
                    balance, cumulative, cumulativeReal,
                    InflationService.percent(curve.rate(m)), curve.isOfficial(m), events));
        }

        ProjectionInflation info = new ProjectionInflation(
                InflationService.percent(curve.projectedRate()),
                screenInflation != null ? "SCREEN" : curve.projectedSource().name(),
                InflationService.percent(base.estimated()), InflationService.percent(base.manual()),
                base.lastOfficialMonth(), InflationService.percent(curve.accumulated(current, last)));
        return new ProjectionResponse(current, last, months, currency, start, info,
                series, summary(series, installmentsEnd, start), excluded(currency, allIncomes, allExpenses));
    }

    private static boolean increased(BigDecimal before, BigDecimal now) {
        return before != null && now.compareTo(before) > 0;
    }

    /** Monto de un gasto en un mes de la proyección. */
    private static BigDecimal amountFor(Expense e, YearMonth m, YearMonth current, Map<UUID, ExpenseOccurrence> currentOcc,
                                        Map<UUID, BigDecimal> variableAverages, InflationCurve curve) {
        if (m.equals(current) && currentOcc.containsKey(e.getId())) {
            return OccurrenceService.effectiveAmount(currentOcc.get(e.getId()));
        }
        if (e.isVariableAmount() && variableAverages.containsKey(e.getId())) {
            return variableAverages.get(e.getId());
        }
        return ExpenseSchedule.estimatedAmount(e, m, curve);
    }

    private static ProjectionSummary summary(List<ProjectionMonth> series, YearMonth installmentsEnd, BigDecimal start) {
        ProjectionMonth last = series.getLast();
        BigDecimal avg = last.cumulative().subtract(start)
                .divide(BigDecimal.valueOf(series.size()), 2, RoundingMode.HALF_UP);
        BigDecimal after = null;
        if (installmentsEnd != null) {
            List<ProjectionMonth> rest = series.stream().filter(p -> p.month().isAfter(installmentsEnd)).toList();
            if (!rest.isEmpty()) {
                after = rest.stream().map(ProjectionMonth::balance).reduce(BigDecimal.ZERO, BigDecimal::add)
                        .divide(BigDecimal.valueOf(rest.size()), 2, RoundingMode.HALF_UP);
            }
        }
        int negative = (int) series.stream().filter(p -> p.balance().signum() < 0).count();
        return new ProjectionSummary(last.cumulative(), last.cumulativeReal(), avg, installmentsEnd, after, negative);
    }

    private static List<String> excluded(String currency, List<Income> incomes, List<Expense> expenses) {
        TreeSet<String> set = new TreeSet<>();
        incomes.forEach(i -> set.add(i.getCurrency()));
        expenses.forEach(e -> set.add(e.getCurrency()));
        set.remove(currency);
        return List.copyOf(set);
    }
}
