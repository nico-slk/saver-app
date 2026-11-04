import { useTranslation } from 'react-i18next';
import clsx from 'clsx';
import { formatMoney } from '@/lib/format';
import type { CurrencyTotals } from './monthsApi';

/** Tarjetas de totales del mes (una fila por moneda). */
export function MonthSummary({ totals, projected, showOverdue = true }: { totals: CurrencyTotals[]; projected: boolean; showOverdue?: boolean }) {
  const { t } = useTranslation();
  if (totals.length === 0) return null;
  return (
    <div className="space-y-3">
      {totals.map((c) => (
        <div key={c.currency} className={clsx('grid grid-cols-2 gap-3', showOverdue ? 'lg:grid-cols-4' : 'lg:grid-cols-3')}>
          <Stat label={t('month.income')} value={formatMoney(c.income, c.currency)} />
          <Stat label={t('month.expenses')} value={formatMoney(c.expenses, c.currency)}
            sub={projected ? t('month.projectedHint') : t('month.pendingOf', { amount: formatMoney(c.pending, c.currency) })} />
          <Stat label={t('month.balance')} value={formatMoney(c.balance, c.currency)} tone={c.balance < 0 ? 'negative' : 'positive'}
            sub={t('month.balanceHint')} />
          {showOverdue && (
            <Stat label={t('month.overdue')} value={formatMoney(c.overdue, c.currency)} tone={c.overdue > 0 ? 'negative' : undefined}
              sub={t('month.overdueHint')} />
          )}
        </div>
      ))}
    </div>
  );
}

function Stat({ label, value, sub, tone }: { label: string; value: string; sub?: string; tone?: 'positive' | 'negative' }) {
  return (
    <div className="min-w-0 rounded-xl border border-slate-200 bg-white p-3 sm:p-4 dark:border-slate-800 dark:bg-slate-900">
      <p className="text-xs font-medium tracking-wide text-slate-500 uppercase">{label}</p>
      <p className={clsx('mt-1 text-base font-semibold whitespace-nowrap tabular-nums sm:text-xl xl:text-2xl', tone === 'negative' && 'text-red-700 dark:text-red-400')}>
        {value}
      </p>
      {sub && <p className="mt-0.5 text-xs text-slate-500">{sub}</p>}
    </div>
  );
}
