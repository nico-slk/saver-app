import { useTranslation } from 'react-i18next';
import { Input } from '@/components/ui';
import type { Currency } from '@/lib/format';
import { formatMonth, type MonthKey } from '@/lib/month';

/** Regla de aumentos periódicos (gastos fijos e ingresos recurrentes). */
export type AdjustmentState = {
  enabled: boolean;
  everyMonths: string;
  mode: 'percent' | 'inflation';
  percent: string;
};

export type AdjustmentSource = {
  adjustEveryMonths: number | null;
  adjustPercent: number | null;
  adjustWithInflation: boolean;
};

export function adjustmentState(src?: AdjustmentSource): AdjustmentState {
  return {
    enabled: !!src?.adjustEveryMonths,
    everyMonths: src?.adjustEveryMonths ? String(src.adjustEveryMonths) : '3',
    mode: src?.adjustWithInflation ? 'inflation' : 'percent',
    percent: src?.adjustPercent != null ? String(src.adjustPercent) : '',
  };
}

/** Campos para la API: null/false si la regla está apagada. */
export function adjustmentPayload(a: AdjustmentState) {
  if (!a.enabled) return { adjustEveryMonths: null, adjustPercent: null, adjustWithInflation: false };
  const inflation = a.mode === 'inflation';
  return {
    adjustEveryMonths: Number(a.everyMonths),
    adjustPercent: inflation ? null : a.percent || '0',
    adjustWithInflation: inflation,
  };
}

/** Texto corto de la regla: "Cada 3 meses según inflación · próximo: ene 2027". */
export function useAdjustmentSummary() {
  const { t } = useTranslation();
  return (src: AdjustmentSource & { nextAdjustmentMonth?: MonthKey | null }) => {
    if (!src.adjustEveryMonths) return null;
    const rule = src.adjustWithInflation
      ? t('adjustment.summaryInflation', { count: src.adjustEveryMonths })
      : t('adjustment.summaryPercent', { count: src.adjustEveryMonths, pct: src.adjustPercent ?? 0 });
    return src.nextAdjustmentMonth
      ? `${rule} · ${t('adjustment.next', { month: formatMonth(src.nextAdjustmentMonth, true) })}`
      : rule;
  };
}

export function AdjustmentFields({
  value,
  onChange,
  currency,
  label,
  hint,
  errors = {},
}: {
  value: AdjustmentState;
  onChange: (v: AdjustmentState) => void;
  currency: Currency;
  label: string;
  hint: string;
  errors?: Record<string, string | undefined>;
}) {
  const { t } = useTranslation();
  const set = <K extends keyof AdjustmentState>(k: K, v: AdjustmentState[K]) => onChange({ ...value, [k]: v });
  return (
    <div className="space-y-3">
      <label className="flex items-start gap-2 text-sm">
        <input type="checkbox" className="mt-0.5" checked={value.enabled} onChange={(e) => set('enabled', e.target.checked)} />
        <span>
          {label}
          <span className="block text-xs text-slate-500">{hint}</span>
        </span>
      </label>
      {value.enabled && (
        <div className="space-y-3 pl-6">
          <fieldset className="flex flex-wrap gap-x-5 gap-y-1 text-sm">
            <legend className="sr-only">{t('adjustment.how')}</legend>
            <label className="flex items-center gap-2">
              <input type="radio" name="adjustMode" checked={value.mode === 'inflation'} onChange={() => set('mode', 'inflation')} />
              {t('adjustment.byInflation')}
            </label>
            <label className="flex items-center gap-2">
              <input type="radio" name="adjustMode" checked={value.mode === 'percent'} onChange={() => set('mode', 'percent')} />
              {t('adjustment.byPercent')}
            </label>
          </fieldset>
          <div className="grid gap-3 sm:grid-cols-2">
            <Input label={t('adjustment.every')} name="adjustEveryMonths" type="number" min="1" max="120"
              value={value.everyMonths} error={errors.adjustEveryMonths} onChange={(e) => set('everyMonths', e.target.value)} />
            {value.mode === 'percent' ? (
              <Input label={t('adjustment.percent')} name="adjustPercent" type="number" step="0.01" min="0" max="1000"
                value={value.percent} error={errors.adjustPercent} onChange={(e) => set('percent', e.target.value)} />
            ) : (
              <p className="self-end pb-2 text-xs text-slate-500">
                {t('adjustment.inflationHint', { count: Number(value.everyMonths) || 1, source: t(`inflation.source.${currency}`) })}
              </p>
            )}
          </div>
        </div>
      )}
    </div>
  );
}
