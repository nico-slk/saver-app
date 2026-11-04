import { useEffect, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import clsx from 'clsx';
import { Alert, Button, Card, Input, Select } from '@/components/ui';
import { Badge, InlineLoader } from '@/components/ui/overlay';
import { StatTile, formatRate } from '@/components/finance/Stats';
import { translateError } from '@/lib/errors';
import { formatMoney } from '@/lib/format';
import { formatMonth } from '@/lib/month';
import { useAuthStore } from '@/stores/authStore';
import { useProjectionPrefs } from '@/stores/uiStore';
import { inflationApi, inflationKeys } from '@/features/inflation/inflationApi';
import { CumulativeLines, OutlookBars } from '@/features/months/charts';
import { projectionsApi, type ProjectionEvent, type ProjectionResponse } from './projectionsApi';

const HORIZONS = [6, 12, 18, 24, 36, 60];

/** Debounce simple para no consultar en cada tecla. */
function useDebounced<T>(value: T, ms = 400): T {
  const [v, setV] = useState(value);
  useEffect(() => {
    const id = setTimeout(() => setV(value), ms);
    return () => clearTimeout(id);
  }, [value, ms]);
  return v;
}

export function ProjectionsPage() {
  const { t } = useTranslation();
  const user = useAuthStore((s) => s.user);
  const qc = useQueryClient();
  const { months, startBalance, setMonths, setStartBalance } = useProjectionPrefs();
  // Vacío = la inflación de la moneda (oficial + manual o estimada); un valor = solo para esta simulación.
  const [inflation, setInflation] = useState('');

  const params = useDebounced({ months, startBalance, inflationPercent: inflation });
  const q = useQuery({
    queryKey: ['projections', params],
    queryFn: () => projectionsApi.get(params),
    placeholderData: (prev) => prev,
  });
  const p = q.data;
  const currency = p?.currency ?? user?.baseCurrency ?? 'ARS';
  const saveInflation = useMutation({
    mutationFn: () => inflationApi.setManual(currency, inflation),
    onSuccess: async () => {
      setInflation('');
      await qc.invalidateQueries({ queryKey: inflationKeys.all });
      await qc.invalidateQueries({ queryKey: ['projections'] });
      await qc.invalidateQueries({ queryKey: ['timeline'] });
    },
  });
  const defaultRate = p && p.inflation.source !== 'SCREEN' ? p.inflation.monthlyPercent : undefined;

  return (
    <div className="space-y-5">
      <div>
        <h1 className="text-2xl font-bold">{t('nav.projections')}</h1>
        <p className="text-sm text-slate-500">{t('projections.intro')}</p>
      </div>

      <Card>
        <div className="grid gap-4 sm:grid-cols-3">
          <Select label={t('projections.horizon')} name="months" value={String(months)} onChange={(e) => setMonths(Number(e.target.value))}>
            {HORIZONS.map((h) => (
              <option key={h} value={h}>{t('projections.monthsOption', { count: h })}</option>
            ))}
          </Select>
          <Input label={t('projections.startBalance')} name="startBalance" type="number" inputMode="decimal" step="0.01" min="0"
            placeholder="0" hint={t('projections.startBalanceHint')} value={startBalance}
            onChange={(e) => setStartBalance(e.target.value)} />
          <div>
            <Input label={t('projections.inflation')} name="inflation" type="number" inputMode="decimal" step="0.1" min="0" max="50"
              placeholder={defaultRate != null ? String(defaultRate) : ''} hint={t('projections.inflationHint')} value={inflation}
              onChange={(e) => setInflation(e.target.value)} />
            {inflation !== '' && (
              <div className="mt-1 flex gap-3">
                <Button variant="ghost" className="px-0 py-1 text-xs text-brand-700 dark:text-brand-500"
                  loading={saveInflation.isPending} onClick={() => saveInflation.mutate()}>
                  {t('projections.saveInflation', { currency })}
                </Button>
                <Button variant="ghost" className="px-0 py-1 text-xs" onClick={() => setInflation('')}>
                  {t('projections.useDefaultInflation')}
                </Button>
              </div>
            )}
          </div>
        </div>
        {p && <InflationInfo p={p} />}
      </Card>

      {q.isError && <Alert>{translateError(t, q.error)}</Alert>}
      {q.isPending && <InlineLoader label={t('common.loading')} />}
      {p && <ProjectionView p={p} />}
    </div>
  );
}

function ProjectionView({ p }: { p: ProjectionResponse }) {
  const { t } = useTranslation();
  const s = p.summary;
  const hasInflation = p.inflation.accumulatedPercent > 0;
  const range = `${formatMonth(p.from, true)} – ${formatMonth(p.to, true)}`;

  return (
    <>
      {p.excludedCurrencies.length > 0 && <Alert>{t('projections.excludedCurrencies', { list: p.excludedCurrencies.join(', '), currency: p.currency })}</Alert>}
      {s.negativeMonths > 0 && <Alert>{t('projections.negativeMonths', { count: s.negativeMonths })}</Alert>}

      <div className="grid grid-cols-2 gap-3 lg:grid-cols-4">
        <StatTile label={t('projections.finalBalance', { count: p.months })} value={formatMoney(s.finalBalance, p.currency)}
          tone={s.finalBalance < 0 ? 'negative' : undefined} sub={range} />
        <StatTile label={t('projections.finalBalanceReal')}
          value={hasInflation ? formatMoney(s.finalBalanceReal, p.currency) : '—'}
          sub={hasInflation ? t('projections.realHint', { pct: formatRate(p.inflation.accumulatedPercent) }) : t('projections.noInflation')} />
        <StatTile label={t('projections.installmentsEnd')}
          value={s.installmentsEndMonth ? formatMonth(s.installmentsEndMonth, true) : t('projections.noInstallments')}
          sub={s.averageSavingsAfterInstallments != null
            ? t('projections.savingsAfter', { amount: formatMoney(s.averageSavingsAfterInstallments, p.currency) })
            : s.installmentsEndMonth ? t('projections.endsBeyond') : undefined} />
        <StatTile label={t('projections.averageSavings')} value={formatMoney(s.averageMonthlySavings, p.currency)}
          tone={s.averageMonthlySavings < 0 ? 'negative' : undefined} sub={t('projections.perMonth')} />
      </div>

      <div className="grid gap-5 lg:grid-cols-2">
        <Card>
          <h2 className="font-semibold">{t('projections.cumulativeTitle')}</h2>
          <p className="mb-3 text-xs text-slate-500">{t('projections.cumulativeHint')}</p>
          <CumulativeLines
            data={p.series.map((m) => ({ month: m.month, nominal: m.cumulative, real: hasInflation ? m.cumulativeReal : undefined }))}
            currency={p.currency}
            labels={{ nominal: t('projections.nominal'), real: t('projections.real') }}
          />
        </Card>
        <Card>
          <h2 className="font-semibold">{t('projections.monthlyTitle')}</h2>
          <p className="mb-3 text-xs text-slate-500">{t('projections.monthlyHint')}</p>
          <OutlookBars data={p.series.map((m) => ({ month: m.month, income: m.income, expenses: m.expenses }))}
            currency={p.currency} label={t('projections.monthlyTitle')} />
        </Card>
      </div>

      <Card>
        <h2 className="mb-3 font-semibold">{t('projections.tableTitle')}</h2>
        <div className="overflow-x-auto">
          <table className="w-full min-w-[940px] text-sm [&_td]:px-2 [&_th]:px-2">
            <thead className="text-left text-xs text-slate-500 uppercase">
              <tr>
                <th className="py-2">{t('history.month')}</th>
                <th className="py-2 text-right">{t('month.income')}</th>
                <th className="py-2 text-right">{t('projections.installments')}</th>
                <th className="py-2 text-right">{t('projections.fixed')}</th>
                <th className="py-2 text-right">{t('month.balance')}</th>
                <th className="py-2 text-right">{t('projections.cumulative')}</th>
                <th className="py-2 text-right">{t('projections.inflationColumn')}</th>
                <th className="py-2 pl-4">{t('projections.events')}</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100 tabular-nums dark:divide-slate-800">
              {p.series.map((m) => (
                <tr key={m.month}>
                  <td className="py-2 whitespace-nowrap">{formatMonth(m.month, true)}</td>
                  <td className="py-2 text-right">{formatMoney(m.income, p.currency)}</td>
                  <td className="py-2 text-right">{m.installments ? formatMoney(m.installments, p.currency) : '—'}</td>
                  <td className="py-2 text-right">{formatMoney(m.recurring + m.oneTime, p.currency)}</td>
                  <td className={clsx('py-2 text-right font-medium', m.balance < 0 && 'text-red-700 dark:text-red-400')}>
                    {formatMoney(m.balance, p.currency)}
                  </td>
                  <td className={clsx('py-2 text-right', m.cumulative < 0 && 'text-red-700 dark:text-red-400')}>
                    {formatMoney(m.cumulative, p.currency)}
                  </td>
                  <td className="py-2 text-right whitespace-nowrap text-slate-600 dark:text-slate-400">
                    {formatRate(m.inflationPercent)}
                    {!m.inflationOfficial && <span className="ml-1 text-xs text-slate-400">{t('projections.estimatedShort')}</span>}
                  </td>
                  <td className="py-2 pl-4">
                    <div className="flex flex-wrap gap-1">
                      {m.events.map((e) => <EventBadge key={e.type + e.refId} e={e} currency={p.currency} />)}
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </Card>
    </>
  );
}

function EventBadge({ e, currency }: { e: ProjectionEvent; currency: import('@/lib/format').Currency }) {
  const { t } = useTranslation();
  const tone = e.type === 'INSTALLMENTS_END' || e.type === 'INCOME_INCREASE' ? 'success' : e.type === 'INCREASE' ? 'warning' : 'neutral';
  return (
    <Badge tone={tone}>
      {t(`projections.event.${e.type}`, { name: e.description, amount: e.amount != null ? formatMoney(e.amount, currency) : '' })}
    </Badge>
  );
}

/** De dónde sale la inflación usada en la simulación. */
function InflationInfo({ p }: { p: ProjectionResponse }) {
  const { t } = useTranslation();
  const i = p.inflation;
  const last = i.lastOfficialMonth ? t('projections.inflationLast', { month: formatMonth(i.lastOfficialMonth, true) }) : null;
  return (
    <p className="mt-3 text-xs text-slate-500">
      {t('projections.inflationUsed', { rate: formatRate(i.monthlyPercent), currency: p.currency })}{' '}
      {t(`projections.inflationSource.${i.source}`)}
      {last && <> · {last}</>}
    </p>
  );
}
