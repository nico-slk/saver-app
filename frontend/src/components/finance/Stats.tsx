import type { ReactNode } from 'react';
import clsx from 'clsx';
import { formatMoney, type Currency } from '@/lib/format';
import { INTL_LOCALE, currentLanguage } from '@/i18n';

export function StatTile({ label, value, sub, tone }: { label: string; value: ReactNode; sub?: ReactNode; tone?: 'negative' | 'positive' }) {
  return (
    <div className="min-w-0 rounded-xl border border-slate-200 bg-white p-3 sm:p-4 dark:border-slate-800 dark:bg-slate-900">
      <p className="text-xs font-medium tracking-wide text-slate-500 uppercase">{label}</p>
      <p className={clsx('mt-1 text-base font-semibold whitespace-nowrap tabular-nums sm:text-xl xl:text-2xl',
        tone === 'negative' && 'text-red-700 dark:text-red-400')}>
        {value}
      </p>
      {sub && <div className="mt-0.5 text-xs text-slate-500">{sub}</div>}
    </div>
  );
}

/** Tasa sin signo (inflación): "1,9 %". */
export function formatRate(value: number, digits = 1): string {
  return new Intl.NumberFormat(INTL_LOCALE[currentLanguage()], { maximumFractionDigits: digits, minimumFractionDigits: digits }).format(value) + ' %';
}

export function formatPct(value: number): string {
  return new Intl.NumberFormat(INTL_LOCALE[currentLanguage()], { maximumFractionDigits: 1, signDisplay: 'exceptZero' }).format(value) + ' %';
}

/**
 * Variación con flecha y signo (nunca solo color). {@code goodWhenUp}: para ingresos/ahorro subir es bueno;
 * para gastos, subir es malo.
 */
export function ChangeText({
  diff,
  percent,
  currency,
  goodWhenUp,
}: {
  diff: number;
  percent: number | null;
  currency: Currency;
  goodWhenUp: boolean;
}) {
  if (diff === 0) return <span className="text-slate-500">= {formatMoney(0, currency)}</span>;
  const up = diff > 0;
  const good = up === goodWhenUp;
  return (
    <span className={clsx('inline-flex items-center gap-1 tabular-nums', good ? 'text-emerald-700 dark:text-emerald-400' : 'text-red-700 dark:text-red-400')}>
      <span aria-hidden>{up ? '▲' : '▼'}</span>
      {up ? '+' : '−'}
      {formatMoney(Math.abs(diff), currency)}
      {percent != null && <span>({formatPct(percent)})</span>}
    </span>
  );
}
