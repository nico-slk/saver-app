import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import { Bar, BarChart, ResponsiveContainer, Tooltip, XAxis } from 'recharts';
import { Alert, Button, Card, Input } from '@/components/ui';
import { InlineLoader } from '@/components/ui/overlay';
import { useChartTheme } from '@/components/charts/chartTheme';
import { TooltipBox } from '@/components/charts/ChartTooltip';
import { formatRate } from '@/components/finance/Stats';
import { translateError } from '@/lib/errors';
import { formatMonth } from '@/lib/month';
import { inflationApi, inflationKeys, type CurrencyInflation } from './inflationApi';

/** Inflación por moneda: datos oficiales, estimación y valor manual (Ajustes). */
export function InflationPanel() {
  const { t } = useTranslation();
  const q = useQuery({ queryKey: inflationKeys.all, queryFn: inflationApi.overview });
  return (
    <Card>
      <h2 className="font-semibold">{t('inflation.title')}</h2>
      <p className="mb-4 text-sm text-slate-500">{t('inflation.intro')}</p>
      {q.isError && <Alert>{translateError(t, q.error)}</Alert>}
      {q.isPending && <InlineLoader label={t('common.loading')} />}
      {q.data && (
        <div className="grid gap-4 lg:grid-cols-2">
          {q.data.currencies.map((c) => <CurrencyCard key={c.currency} c={c} />)}
        </div>
      )}
    </Card>
  );
}

/** Texto de la tasa usada para proyectar: "1,9 % · promedio de los últimos 3 meses". */
export function projectedLabel(t: (k: string, o?: Record<string, unknown>) => string, c: Pick<CurrencyInflation, 'projectedPercent' | 'projectedSource' | 'estimateMonths'>) {
  if (c.projectedSource === 'NONE') return t('inflation.projected.NONE');
  return `${formatRate(c.projectedPercent)} · ${t(`inflation.projected.${c.projectedSource}`, { count: c.estimateMonths })}`;
}

function CurrencyCard({ c }: { c: CurrencyInflation }) {
  const { t } = useTranslation();
  const qc = useQueryClient();
  const [manual, setManual] = useState(c.manualPercent != null ? String(c.manualPercent) : '');
  const done = async () => {
    await qc.invalidateQueries({ queryKey: inflationKeys.all });
    await qc.invalidateQueries({ queryKey: ['projections'] });
    await qc.invalidateQueries({ queryKey: ['timeline'] });
    await qc.invalidateQueries({ queryKey: ['history'] });
    await qc.invalidateQueries({ queryKey: ['months'] });
  };
  const save = useMutation({ mutationFn: () => inflationApi.setManual(c.currency, manual), onSuccess: done });
  const clear = useMutation({
    mutationFn: () => inflationApi.clearManual(c.currency),
    onSuccess: async () => {
      setManual('');
      await done();
    },
  });
  const hasData = c.lastOfficialMonth != null;

  return (
    <section className="rounded-lg border border-slate-200 p-4 dark:border-slate-800">
      <h3 className="font-medium">{t(`inflation.currency.${c.currency}`)}</h3>
      <p className="text-xs text-slate-500">{t(`inflation.source.${c.currency}`)}</p>

      {hasData ? (
        <>
          <dl className="mt-3 grid grid-cols-2 gap-3 text-sm">
            <div>
              <dt className="text-xs text-slate-500">{t('inflation.lastOfficial', { month: formatMonth(c.lastOfficialMonth!, true) })}</dt>
              <dd className="font-semibold tabular-nums">{formatRate(c.lastOfficialPercent ?? 0)}</dd>
            </div>
            <div>
              <dt className="text-xs text-slate-500">{t('inflation.last12')}</dt>
              <dd className="font-semibold tabular-nums">{c.last12MonthsPercent != null ? formatRate(c.last12MonthsPercent) : '—'}</dd>
            </div>
          </dl>
          <RateBars data={c.recent} />
        </>
      ) : (
        <p className="mt-3 text-sm text-slate-500">{t('inflation.noData')}</p>
      )}

      <div className="mt-3 rounded-md bg-slate-50 p-3 text-sm dark:bg-slate-800/50">
        <p className="text-xs text-slate-500">{t('inflation.usedForProjections')}</p>
        <p className="font-semibold">{projectedLabel(t, c)}</p>
      </div>

      <form className="mt-3 flex flex-wrap items-end gap-2" noValidate
        onSubmit={(e) => { e.preventDefault(); if (manual !== '') save.mutate(); }}>
        <div className="w-52">
          <Input label={t('inflation.manual')} name={`manual-${c.currency}`} type="number" inputMode="decimal"
            step="0.1" min="0" max="50" placeholder={c.estimatedPercent != null ? String(c.estimatedPercent) : '0'}
            value={manual} onChange={(e) => setManual(e.target.value)} />
        </div>
        <Button type="submit" variant="secondary" loading={save.isPending} disabled={manual === ''}>{t('common.save')}</Button>
        {c.manualPercent != null && (
          <Button type="button" variant="ghost" loading={clear.isPending} onClick={() => clear.mutate()}>
            {hasData ? t('inflation.useEstimate') : t('inflation.clearManual')}
          </Button>
        )}
      </form>
      <p className="mt-1 text-xs text-slate-500">{t('inflation.manualHint')}</p>
      {(save.isError || clear.isError) && <Alert>{translateError(t, save.error ?? clear.error)}</Alert>}
    </section>
  );
}

/** Inflación de los últimos meses: una serie, barras chicas. */
function RateBars({ data }: { data: { month: string; percent: number }[] }) {
  const { t } = useTranslation();
  const c = useChartTheme();
  if (data.length === 0) return null;
  return (
    <div className="mt-3 h-24" role="img" aria-label={t('inflation.recentChart')}>
      <ResponsiveContainer width="100%" height="100%">
        <BarChart data={data} margin={{ top: 4, right: 0, bottom: 0, left: 0 }} barCategoryGap="18%">
          <XAxis dataKey="month" tickFormatter={(m: string) => formatMonth(m, true).split(' ')[0] ?? m} stroke={c.axis}
            tick={{ fill: c.axis, fontSize: 10 }} tickLine={false} axisLine={{ stroke: c.grid }} interval="preserveStartEnd" minTickGap={8} />
          <Tooltip
            cursor={{ fill: c.grid, opacity: 0.5 }}
            content={({ active, payload, label }) =>
              active && payload?.[0] ? (
                <TooltipBox title={formatMonth(String(label))}
                  rows={[{ color: c.series1, label: t('inflation.monthly'), value: formatRate(Number(payload[0].value)) }]} />
              ) : null
            }
          />
          <Bar dataKey="percent" fill={c.series1} radius={[3, 3, 0, 0]} maxBarSize={18} />
        </BarChart>
      </ResponsiveContainer>
    </div>
  );
}
