import { useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import clsx from 'clsx';
import { Alert, Card, Input } from '@/components/ui';
import { Badge, EmptyState, InlineLoader } from '@/components/ui/overlay';
import { ChangeText, StatTile, formatPct, formatRate } from '@/components/finance/Stats';
import { translateError } from '@/lib/errors';
import { formatMoney } from '@/lib/format';
import { addMonths, formatMonth, isMonthKey, monthKey, type MonthKey } from '@/lib/month';
import { useCategories } from '@/features/categories/useCategories';
import { CategoryChip } from '@/features/expenses/parts';
import { CategoryBars, OutlookBars, SavingsBars } from '@/features/months/charts';
import { historyApi, type ExpenseChange, type HistoryMonth } from './historyApi';

const PRESETS = [3, 6, 12, 24] as const;

export function HistoryPage() {
  const { t } = useTranslation();
  const [params, setParams] = useSearchParams();
  const current = monthKey();
  const to = isMonthKey(params.get('to')) ? params.get('to')! : current;
  const from = isMonthKey(params.get('from')) ? params.get('from')! : addMonths(to, -5);
  const categories = (params.get('cat') ?? '').split(',').filter(Boolean);
  // Valores ajustados por inflación: todo expresado en moneda del último mes del período.
  const real = params.get('real') === '1';

  const update = (next: Record<string, string | null>) => {
    const p = new URLSearchParams(params);
    Object.entries(next).forEach(([k, v]) => (v ? p.set(k, v) : p.delete(k)));
    setParams(p, { replace: true });
  };

  const q = useQuery({
    queryKey: ['history', from, to, categories.join(',')],
    queryFn: () => historyApi.get({ from, to, categories }),
  });
  const h = q.data;
  const adj = (m: HistoryMonth, v: number) => (real ? v * m.realFactor : v);

  return (
    <div className="space-y-5">
      <h1 className="text-2xl font-bold">{t('nav.history')}</h1>

      <Card>
        <div className="flex flex-wrap items-end gap-3">
          <div className="flex flex-wrap gap-1" role="group" aria-label={t('history.range')}>
            {PRESETS.map((n) => {
              const active = to === current && from === addMonths(current, -(n - 1));
              return (
                <button key={n} type="button" aria-pressed={active}
                  onClick={() => update({ from: addMonths(current, -(n - 1)), to: current })}
                  className={clsx('rounded-lg border px-3 py-1.5 text-sm transition',
                    active ? 'border-brand-700 bg-brand-50 text-brand-800 dark:bg-brand-800/30 dark:text-brand-100'
                      : 'border-slate-300 hover:bg-slate-50 dark:border-slate-700 dark:hover:bg-slate-800')}>
                  {t('history.lastMonths', { count: n })}
                </button>
              );
            })}
          </div>
          <div className="flex gap-2">
            <Input label={t('history.from')} name="from" type="month" max={to} value={from} className="w-40"
              onChange={(e) => isMonthKey(e.target.value) && update({ from: e.target.value })} />
            <Input label={t('history.to')} name="to" type="month" min={from} max={current} value={to} className="w-40"
              onChange={(e) => isMonthKey(e.target.value) && update({ to: e.target.value })} />
          </div>
        </div>
        <CategoryFilter selected={categories} onChange={(ids) => update({ cat: ids.join(',') || null })} />
        <label className="mt-4 flex items-start gap-2 text-sm">
          <input type="checkbox" className="mt-0.5" checked={real} onChange={(e) => update({ real: e.target.checked ? '1' : null })} />
          <span>
            {t('history.realToggle', { month: formatMonth(to, true) })}
            <span className="block text-xs text-slate-500">{t('history.realToggleHint')}</span>
          </span>
        </label>
      </Card>

      {q.isError && <Alert>{translateError(t, q.error)}</Alert>}
      {q.isPending && <InlineLoader label={t('common.loading')} />}
      {h && (
        <>
          <div className="grid grid-cols-2 gap-3 lg:grid-cols-4">
            <StatTile label={t('history.savingsOf', { month: formatMonth(h.to, true) })}
              value={formatMoney(h.summary.savingsVsPreviousMonth.to, h.currency)}
              tone={h.summary.savingsVsPreviousMonth.to < 0 ? 'negative' : undefined}
              sub={<>{t('history.vsPrevious')}{' '}
                <ChangeText diff={h.summary.savingsVsPreviousMonth.diff} percent={h.summary.savingsVsPreviousMonth.percent}
                  currency={h.currency} goodWhenUp /></>} />
            <StatTile label={t('history.averageSavings')} value={formatMoney(h.summary.averageSavings, h.currency)}
              sub={t('history.perMonth', { count: h.months.length })} />
            <StatTile label={t('history.totalSavings')} value={formatMoney(h.summary.totalSavings, h.currency)}
              tone={h.summary.totalSavings < 0 ? 'negative' : undefined}
              sub={`${formatMonth(h.from, true)} – ${formatMonth(h.to, true)}`} />
            <StatTile label={categories.length ? t('history.filteredExpensesChange') : t('history.expensesChange')}
              value={h.summary.expensesFirstToLast.percent != null
                ? <ChangeText diff={h.summary.expensesFirstToLast.diff} percent={null} currency={h.currency} goodWhenUp={false} />
                : formatMoney(h.summary.expensesFirstToLast.diff, h.currency)}
              sub={h.summary.expensesFirstToLast.percent != null
                ? t('history.firstToLast', { pct: h.summary.expensesFirstToLast.percent.toLocaleString() })
                : t('history.firstToLastNoPct')} />
          </div>
          <p className="text-sm text-slate-600 dark:text-slate-300">
            {t('history.periodInflation', { currency: h.currency, pct: formatRate(h.summary.inflationPercent, 2),
              from: formatMonth(h.from, true), to: formatMonth(h.to, true) })}
            {h.summary.inflationEstimated && <span className="text-slate-500"> {t('history.includesEstimate')}</span>}
            {h.summary.expensesRealPercent != null && (
              <> · {t('history.expensesReal', { pct: formatPct(h.summary.expensesRealPercent) })}</>
            )}
            {' · '}{t('history.totalSavingsReal', { month: formatMonth(h.to, true), amount: formatMoney(h.summary.totalSavingsReal, h.currency) })}
          </p>

          {h.months.every((m) => m.income === 0 && m.expensesTotal === 0) ? (
            <EmptyState title={t('history.empty')} />
          ) : (
            <>
              <div className="grid gap-5 lg:grid-cols-2">
                <Card>
                  <h2 className="font-semibold">{t('history.incomeVsExpenses')}</h2>
                  {categories.length > 0 && <p className="text-xs text-slate-500">{t('history.filterApplied')}</p>}
                  {real && <p className="text-xs text-slate-500">{t('history.realApplied', { month: formatMonth(h.to, true) })}</p>}
                  <div className="mt-3">
                    <OutlookBars data={h.months.map((m) => ({ month: m.month, income: adj(m, m.income), expenses: adj(m, m.expenses) }))}
                      currency={h.currency} label={t('history.incomeVsExpenses')} />
                  </div>
                </Card>
                <Card>
                  <h2 className="font-semibold">{t('history.savingsByMonth')}</h2>
                  <p className="text-xs text-slate-500">{t('history.savingsHint')}</p>
                  <div className="mt-3">
                    <SavingsBars data={h.months.map((m) => ({ month: m.month, value: adj(m, m.savings) }))} currency={h.currency}
                      label={t('history.savings')} />
                  </div>
                </Card>
              </div>
              <PeriodByCategory months={real ? h.months.map((m) => ({ byCategory: m.byCategory.map((c) => ({ ...c, amount: adj(m, c.amount) })) })) : h.months} currency={h.currency} />
              <MonthlyTable h={h} real={real} />
            </>
          )}
          <Comparison defaultFrom={h.from} to={h.to} />
        </>
      )}
    </div>
  );
}

function CategoryFilter({ selected, onChange }: { selected: string[]; onChange: (ids: string[]) => void }) {
  const { t } = useTranslation();
  const { all, label } = useCategories();
  const toggle = (id: string) => onChange(selected.includes(id) ? selected.filter((x) => x !== id) : [...selected, id]);
  return (
    <div className="mt-4">
      <p className="mb-2 text-sm font-medium text-slate-700 dark:text-slate-300">{t('history.filterByCategory')}</p>
      <div className="flex flex-wrap gap-2">
        <button type="button" aria-pressed={selected.length === 0} onClick={() => onChange([])}
          className={clsx('rounded-full border px-3 py-1 text-xs', selected.length === 0
            ? 'border-brand-700 bg-brand-700 text-white' : 'border-slate-300 dark:border-slate-700')}>
          {t('history.allCategories')}
        </button>
        {all.map((c) => (
          <button key={c.id} type="button" aria-pressed={selected.includes(c.id)} onClick={() => toggle(c.id)}
            className={clsx('inline-flex items-center gap-1.5 rounded-full border px-3 py-1 text-xs',
              selected.includes(c.id) ? 'border-brand-700 bg-brand-50 text-brand-800 dark:bg-brand-800/30 dark:text-brand-100'
                : 'border-slate-300 dark:border-slate-700', c.deleted && 'opacity-70')}>
            <span className="size-2 rounded-full" style={{ backgroundColor: c.color }} aria-hidden />
            {label(c)}
          </button>
        ))}
      </div>
    </div>
  );
}

function PeriodByCategory({ months, currency }: { months: { byCategory: { categoryId: string; amount: number }[] }[]; currency: import('@/lib/format').Currency }) {
  const { t } = useTranslation();
  const { nameOf } = useCategories();
  const totals = new Map<string, number>();
  months.forEach((m) => m.byCategory.forEach((c) => totals.set(c.categoryId, (totals.get(c.categoryId) ?? 0) + c.amount)));
  const data = [...totals.entries()].sort((a, b) => b[1] - a[1]).map(([id, amount]) => ({ name: nameOf(id), amount }));
  if (data.length === 0) return null;
  return (
    <Card>
      <h2 className="mb-3 font-semibold">{t('history.byCategoryPeriod')}</h2>
      <CategoryBars data={data} currency={currency} />
    </Card>
  );
}

function MonthlyTable({ h, real }: { h: import('./historyApi').HistoryResponse; real: boolean }) {
  const { t } = useTranslation();
  const [open, setOpen] = useState(false);
  const v = (m: HistoryMonth, x: number) => (real ? x * m.realFactor : x);
  return (
    <Card>
      <button type="button" onClick={() => setOpen(!open)} aria-expanded={open} className="flex w-full items-center justify-between font-semibold">
        {t('history.tableTitle')}
        <span aria-hidden>{open ? '−' : '+'}</span>
      </button>
      {open && (
        <div className="mt-3 overflow-x-auto">
          <table className="w-full min-w-[560px] text-sm [&_td]:px-2 [&_th]:px-2">
            <thead className="text-left text-xs text-slate-500 uppercase">
              <tr>
                <th className="py-2">{t('history.month')}</th>
                <th className="py-2 text-right">{t('month.income')}</th>
                <th className="py-2 text-right">{t('month.expenses')}</th>
                <th className="py-2 text-right">{t('history.savings')}</th>
                <th className="py-2 text-right">{t('projections.inflationColumn')}</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100 tabular-nums dark:divide-slate-800">
              {h.months.map((m) => (
                <tr key={m.month}>
                  <td className="py-2">{formatMonth(m.month)}</td>
                  <td className="py-2 text-right">{formatMoney(v(m, m.income), h.currency)}</td>
                  <td className="py-2 text-right">{formatMoney(v(m, m.expenses), h.currency)}</td>
                  <td className={clsx('py-2 text-right font-medium', m.savings < 0 && 'text-red-700 dark:text-red-400')}>
                    {formatMoney(v(m, m.savings), h.currency)}
                  </td>
                  <td className="py-2 text-right whitespace-nowrap text-slate-600 dark:text-slate-400">
                    {formatRate(m.inflationPercent)}
                    {!m.inflationOfficial && <span className="ml-1 text-xs text-slate-400">{t('projections.estimatedShort')}</span>}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </Card>
  );
}

const STATUS_TONE = { UP: 'danger', DOWN: 'success', SAME: 'neutral', NEW: 'info', ENDED: 'neutral' } as const;

/** Compara un mes elegido con el último del rango: qué gastos subieron, cuánto y en qué %. */
function Comparison({ defaultFrom, to }: { defaultFrom: MonthKey; to: MonthKey }) {
  const { t } = useTranslation();
  const [from, setFrom] = useState<MonthKey>(defaultFrom);
  const effectiveFrom = from > to ? defaultFrom : from;
  const q = useQuery({ queryKey: ['history', 'compare', effectiveFrom, to], queryFn: () => historyApi.compare(effectiveFrom, to) });
  const c = q.data;

  return (
    <Card>
      <div className="mb-4 flex flex-wrap items-end justify-between gap-3">
        <div>
          <h2 className="font-semibold">{t('history.compareTitle')}</h2>
          <p className="text-xs text-slate-500">{t('history.compareHint', { to: formatMonth(to) })}</p>
        </div>
        <Input label={t('history.compareFrom')} name="compareFrom" type="month" max={to} value={effectiveFrom} className="w-44"
          onChange={(e) => isMonthKey(e.target.value) && setFrom(e.target.value)} />
      </div>
      {q.isError && <Alert>{translateError(t, q.error)}</Alert>}
      {q.isPending && <InlineLoader label={t('common.loading')} />}
      {c && (
        <div className="space-y-5">
          <div className="grid grid-cols-1 gap-3 sm:grid-cols-3">
            {([
              ['month.income', c.income, true],
              ['month.expenses', c.expenses, false],
              ['history.savings', c.savings, true],
            ] as const).map(([key, ch, goodUp]) => (
              <div key={key} className="rounded-lg bg-slate-50 p-3 dark:bg-slate-800/50">
                <p className="text-xs text-slate-500 uppercase">{t(key)}</p>
                <p className="text-sm tabular-nums">
                  {formatMoney(ch.from, c.currency)} → <strong>{formatMoney(ch.to, c.currency)}</strong>
                </p>
                <ChangeText diff={ch.diff} percent={ch.percent} currency={c.currency} goodWhenUp={goodUp} />
              </div>
            ))}
          </div>
          <p className="text-sm text-slate-600 dark:text-slate-300">
            {t('history.averageSince', { from: formatMonth(c.from), count: c.months, amount: formatMoney(c.averageSavings, c.currency) })}
          </p>
          <div className="rounded-lg border border-slate-200 p-3 text-sm dark:border-slate-800">
            <p>
              {t('history.compareInflation', { from: formatMonth(c.from, true), to: formatMonth(c.to, true), pct: formatRate(c.inflationPercent, 2) })}
              {c.inflationEstimated && <span className="text-slate-500"> {t('history.includesEstimate')}</span>}
            </p>
            {c.expensesRealPercent != null && (
              <p className="mt-1 text-slate-600 dark:text-slate-300">
                {t(c.expensesRealPercent > 0 ? 'history.expensesAboveInflation' : c.expensesRealPercent < 0 ? 'history.expensesBelowInflation' : 'history.expensesLikeInflation',
                  { pct: formatRate(Math.abs(c.expensesRealPercent), 1) })}
              </p>
            )}
            <p className="mt-1 text-slate-600 dark:text-slate-300">
              {t('history.savingsRealCompare', { from: formatMonth(c.from, true), to: formatMonth(c.to, true),
                amount: formatMoney(c.savingsReal.from, c.currency) })}{' '}
              <ChangeText diff={c.savingsReal.diff} percent={c.savingsReal.percent} currency={c.currency} goodWhenUp />
            </p>
          </div>
          {c.items.length === 0 ? (
            <p className="text-sm text-slate-500">{t('history.noExpensesToCompare')}</p>
          ) : (
            <div className="overflow-x-auto">
              <table className="w-full min-w-[680px] text-sm [&_td]:px-2 [&_th]:px-2">
                <thead className="text-left text-xs text-slate-500 uppercase">
                  <tr>
                    <th className="py-2">{t('history.expense')}</th>
                    <th className="py-2 text-right">{formatMonth(c.from, true)}</th>
                    <th className="py-2 text-right">{formatMonth(c.to, true)}</th>
                    <th className="py-2 text-right">{t('history.change')}</th>
                    <th className="py-2 text-right">{t('history.vsInflation')}</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-100 tabular-nums dark:divide-slate-800">
                  {c.items.map((i: ExpenseChange) => (
                    <tr key={i.expenseId}>
                      <td className="py-2">
                        <div className="flex flex-wrap items-center gap-2">
                          <span className="font-medium">{i.description}</span>
                          <Badge tone={STATUS_TONE[i.status]}>{t(`history.status.${i.status}`)}</Badge>
                        </div>
                        <CategoryChip id={i.categoryId} />
                      </td>
                      <td className="py-2 text-right">{i.from == null ? '—' : formatMoney(i.from, c.currency)}</td>
                      <td className="py-2 text-right">{i.to == null ? '—' : formatMoney(i.to, c.currency)}</td>
                      <td className="py-2 text-right">
                        <ChangeText diff={i.diff} percent={i.percent} currency={c.currency} goodWhenUp={false} />
                      </td>
                      <td className="py-2 text-right">
                        <RealChange pct={i.realPercent} />
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </div>
      )}
    </Card>
  );
}

/** Variación real de un gasto: por encima (↑, malo) o por debajo (↓, bueno) de la inflación. */
function RealChange({ pct }: { pct?: number }) {
  const { t } = useTranslation();
  if (pct == null) return <span className="text-slate-400">—</span>;
  if (Math.abs(pct) < 0.05) return <span className="text-slate-500">{t('history.likeInflation')}</span>;
  const above = pct > 0;
  return (
    <span className={above ? 'text-red-700 dark:text-red-400' : 'text-emerald-700 dark:text-emerald-400'}>
      {above ? '▲' : '▼'} {t(above ? 'history.aboveInflation' : 'history.belowInflation', { pct: formatRate(Math.abs(pct), 1) })}
    </span>
  );
}
