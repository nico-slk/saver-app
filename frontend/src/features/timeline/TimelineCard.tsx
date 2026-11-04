import { useQuery } from '@tanstack/react-query';
import { useSearchParams } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { Bar, BarChart, CartesianGrid, Cell, Legend, ReferenceArea, ReferenceLine, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts';
import { Alert, Button, Card, Input } from '@/components/ui';
import { InlineLoader } from '@/components/ui/overlay';
import { useChartTheme } from '@/components/charts/chartTheme';
import { TooltipBox } from '@/components/charts/ChartTooltip';
import { INTL_LOCALE, currentLanguage } from '@/i18n';
import { translateError } from '@/lib/errors';
import { formatMoney, type Currency } from '@/lib/format';
import { addMonths, formatMonth, isMonthKey, monthKey, type MonthKey } from '@/lib/month';
import { timelineApi, type TimelinePoint } from './timelineApi';

/** Por defecto: 6 meses antes, el actual y 6 después. */
export const TIMELINE_BEFORE = 6;
export const TIMELINE_MONTHS = 13;
const MAX_MONTHS = 120;

const compact = (v: number) =>
  new Intl.NumberFormat(INTL_LOCALE[currentLanguage()], { notation: 'compact', maximumFractionDigits: 1 }).format(v);

/**
 * Ingresos y gastos de un rango de meses (pasado real + futuro proyectado) con el día de hoy marcado.
 * El rango vive en la URL (?from=AAAA-MM&months=N) para poder compartirlo y volver atrás.
 */
export function TimelineCard({ currency }: { currency: Currency }) {
  const { t } = useTranslation();
  const [params, setParams] = useSearchParams();
  const defaultFrom = addMonths(monthKey(), -TIMELINE_BEFORE);
  const fromParam = params.get('from');
  const from: MonthKey = isMonthKey(fromParam) ? fromParam : defaultFrom;
  const monthsParam = Number(params.get('months'));
  const months = Number.isInteger(monthsParam) && monthsParam >= 1 && monthsParam <= MAX_MONTHS ? monthsParam : TIMELINE_MONTHS;
  const isDefault = from === defaultFrom && months === TIMELINE_MONTHS;

  const update = (next: { from?: string; months?: string }) =>
    setParams((p) => {
      const out = new URLSearchParams(p);
      if (next.from !== undefined) (isMonthKey(next.from) ? out.set('from', next.from) : out.delete('from'));
      if (next.months !== undefined) (next.months ? out.set('months', next.months) : out.delete('months'));
      return out;
    }, { replace: true });

  const q = useQuery({
    queryKey: ['timeline', from, months, currency],
    queryFn: () => timelineApi.get({ from, months, currency }),
    placeholderData: (prev) => prev,
  });

  return (
    <Card>
      <div className="mb-3 flex flex-wrap items-end justify-between gap-3">
        <div>
          <h2 className="font-semibold">{t('timeline.title')}</h2>
          <p className="text-xs text-slate-500">{t('timeline.hint')}</p>
        </div>
        <div className="flex flex-wrap items-end gap-2">
          <div className="w-40">
            <Input label={t('timeline.from')} name="timelineFrom" type="month" value={from}
              onChange={(e) => update({ from: e.target.value })} />
          </div>
          <div className="w-24">
            <Input label={t('timeline.months')} name="timelineMonths" type="number" min="1" max={MAX_MONTHS}
              value={params.get('months') ?? String(months)} onChange={(e) => update({ months: e.target.value })} />
          </div>
          {!isDefault && (
            <Button variant="ghost" className="mb-0.5 px-2 text-sm" onClick={() => setParams({}, { replace: true })}>
              {t('timeline.reset')}
            </Button>
          )}
        </div>
      </div>
      {q.isError && <Alert>{translateError(t, q.error)}</Alert>}
      {q.isPending && <InlineLoader label={t('common.loading')} />}
      {q.data && (
        <>
          <p className="mb-2 text-xs text-slate-500">
            {t('timeline.range', { from: formatMonth(q.data.from, true), to: formatMonth(q.data.to, true) })}
          </p>
          <TimelineBars points={q.data.points} current={q.data.current} today={q.data.today} currency={currency} />
        </>
      )}
    </Card>
  );
}

/** Barras agrupadas ingresos/gastos; los meses proyectados con menos opacidad y fondo sombreado. */
function TimelineBars({ points, current, today, currency }: { points: TimelinePoint[]; current: MonthKey; today: string; currency: Currency }) {
  const { t } = useTranslation();
  const c = useChartTheme();
  const labels = { income: t('month.income'), expenses: t('month.expenses') };
  const future = points.filter((p) => p.type === 'FUTURE');
  const hasCurrent = points.some((p) => p.month === current);
  const [y, m, d] = today.split('-').map(Number) as [number, number, number];
  const todayLabel = t('timeline.today', {
    date: new Intl.DateTimeFormat(INTL_LOCALE[currentLanguage()], { day: 'numeric', month: 'short' }).format(new Date(y, m - 1, d)),
  });
  const faded = (p: TimelinePoint) => (p.type === 'FUTURE' ? 0.45 : 1);
  const ariaLabel = `${t('timeline.title')}: ${points.map((p) => `${formatMonth(p.month, true)} ${formatMoney(p.income, currency)} / ${formatMoney(p.expenses, currency)}`).join(', ')}`;

  return (
    <div className="h-72" role="img" aria-label={ariaLabel}>
      <ResponsiveContainer width="100%" height="100%">
        <BarChart data={points} margin={{ top: 22, right: 8, bottom: 0, left: 0 }} barGap={2} barCategoryGap="22%">
          <CartesianGrid vertical={false} stroke={c.grid} />
          {future.length > 0 && (
            <ReferenceArea x1={future[0]!.month} x2={future[future.length - 1]!.month} fill={c.grid} fillOpacity={0.45}
              ifOverflow="extendDomain"
              label={{ value: t('timeline.projected'), position: 'top', fill: c.axis, fontSize: 11 }} />
          )}
          <XAxis dataKey="month" tickFormatter={(mk: string) => formatMonth(mk, true)} stroke={c.axis}
            tick={{ fill: c.axis, fontSize: 11 }} tickLine={false} axisLine={{ stroke: c.grid }} minTickGap={12} />
          <YAxis tickFormatter={compact} width={48} stroke={c.axis} tick={{ fill: c.axis, fontSize: 11 }} tickLine={false} axisLine={false} />
          {hasCurrent && (
            <ReferenceLine x={current} position={d <= 10 ? 'start' : d <= 20 ? 'middle' : 'end'}
              stroke={c.text} strokeDasharray="4 3" strokeWidth={1.5}
              label={{ value: todayLabel, position: 'top', fill: c.text, fontSize: 11, fontWeight: 600 }} />
          )}
          <Tooltip
            cursor={{ fill: c.grid, opacity: 0.5 }}
            content={({ active, payload, label }) => {
              if (!active || !payload?.length) return null;
              const p = payload[0]!.payload as TimelinePoint;
              return (
                <TooltipBox
                  title={`${formatMonth(String(label))} · ${t(`timeline.type.${p.type}`)}`}
                  rows={[
                    { color: c.series1, label: labels.income, value: formatMoney(p.income, currency) },
                    { color: c.series2, label: labels.expenses, value: formatMoney(p.expenses, currency) },
                    { color: 'transparent', label: t('month.balance'), value: formatMoney(p.balance, currency) },
                  ]}
                />
              );
            }}
          />
          <Legend itemSorter={null} iconType="square" iconSize={10} formatter={(v: string) => (
            <span style={{ color: c.text, fontSize: 12 }}>{labels[v as 'income' | 'expenses']}</span>
          )} />
          <Bar dataKey="income" fill={c.series1} radius={[4, 4, 0, 0]} maxBarSize={26}>
            {points.map((p) => <Cell key={p.month} fillOpacity={faded(p)} />)}
          </Bar>
          <Bar dataKey="expenses" fill={c.series2} radius={[4, 4, 0, 0]} maxBarSize={26}>
            {points.map((p) => <Cell key={p.month} fillOpacity={faded(p)} />)}
          </Bar>
        </BarChart>
      </ResponsiveContainer>
    </div>
  );
}
