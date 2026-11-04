import { useState, type FormEvent } from 'react';
import { useMutation } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import { Alert, Button, Input } from '@/components/ui';
import { Modal } from '@/components/ui/overlay';
import { translateError } from '@/lib/errors';
import { monthKey, type MonthKey } from '@/lib/month';
import { useFinanceInvalidation } from '@/features/months/useFinanceInvalidation';

/** Registrar un nuevo monto vigente desde un mes (aumento de alquiler, de sueldo, nueva tarifa). */
export function AmountChangeDialog({
  title,
  hint,
  currentAmount,
  minMonth,
  onSubmit,
  onClose,
}: {
  title: string;
  hint: string;
  currentAmount: number | null;
  minMonth: MonthKey;
  onSubmit: (input: { effectiveMonth: MonthKey; amount: string }) => Promise<unknown>;
  onClose: () => void;
}) {
  const { t } = useTranslation();
  const refresh = useFinanceInvalidation();
  const now = monthKey();
  const [form, setForm] = useState({ effectiveMonth: now < minMonth ? minMonth : now, amount: '' });
  const save = useMutation({
    mutationFn: () => onSubmit(form),
    onSuccess: async () => {
      await refresh();
      onClose();
    },
  });
  const pct = currentAmount && Number(form.amount) > 0 ? (Number(form.amount) / currentAmount - 1) * 100 : null;
  const submit = (e: FormEvent) => {
    e.preventDefault();
    save.mutate();
  };
  return (
    <Modal open onClose={onClose} title={title}>
      <form onSubmit={submit} className="space-y-4">
        {save.isError && <Alert>{translateError(t, save.error)}</Alert>}
        <p className="text-sm text-slate-500">{hint}</p>
        <Input label={t('finance.effectiveFrom')} name="effectiveMonth" type="month" min={minMonth} required
          value={form.effectiveMonth} onChange={(e) => setForm({ ...form, effectiveMonth: e.target.value })} />
        <Input label={t('finance.newAmount')} name="amount" type="number" inputMode="decimal" step="0.01" min="0.01" required
          value={form.amount} onChange={(e) => setForm({ ...form, amount: e.target.value })}
          hint={pct !== null ? t('finance.changePct', { pct: (pct >= 0 ? '+' : '') + pct.toFixed(1) }) : undefined} />
        <div className="flex justify-end gap-2">
          <Button type="button" variant="secondary" onClick={onClose}>{t('common.cancel')}</Button>
          <Button type="submit" loading={save.isPending}>{t('common.save')}</Button>
        </div>
      </form>
    </Modal>
  );
}

/** Dar de baja: último mes en que aplica (gasto fijo o ingreso recurrente). */
export function EndDialog({
  title,
  hint,
  minMonth,
  onSubmit,
  onClose,
}: {
  title: string;
  hint: string;
  minMonth: MonthKey;
  onSubmit: (endMonth: MonthKey) => Promise<unknown>;
  onClose: () => void;
}) {
  const { t } = useTranslation();
  const refresh = useFinanceInvalidation();
  const now = monthKey();
  const [end, setEnd] = useState(now < minMonth ? minMonth : now);
  const save = useMutation({
    mutationFn: () => onSubmit(end),
    onSuccess: async () => {
      await refresh();
      onClose();
    },
  });
  return (
    <Modal open onClose={onClose} title={title}>
      <form
        onSubmit={(e) => {
          e.preventDefault();
          save.mutate();
        }}
        className="space-y-4"
      >
        {save.isError && <Alert>{translateError(t, save.error)}</Alert>}
        <p className="text-sm text-slate-500">{hint}</p>
        <Input label={t('finance.lastMonth')} name="endMonth" type="month" min={minMonth} required value={end}
          onChange={(e) => setEnd(e.target.value)} />
        <div className="flex justify-end gap-2">
          <Button type="button" variant="secondary" onClick={onClose}>{t('common.cancel')}</Button>
          <Button type="submit" loading={save.isPending}>{t('common.save')}</Button>
        </div>
      </form>
    </Modal>
  );
}

/** Selector de mes con flechas y botón "Hoy". */
export function MonthNavigator({ value, onChange, label }: { value: MonthKey; onChange: (m: MonthKey) => void; label: string }) {
  const { t } = useTranslation();
  const [y, m] = value.split('-').map(Number) as [number, number];
  const shift = (d: number) => onChange(monthKey(new Date(y, m - 1 + d, 1)));
  const isNow = value === monthKey();
  return (
    <div className="flex items-center gap-1">
      <Button variant="ghost" className="px-2" onClick={() => shift(-1)} aria-label={t('finance.prevMonth')}>‹</Button>
      <span className="min-w-36 text-center font-semibold">{label}</span>
      <Button variant="ghost" className="px-2" onClick={() => shift(1)} aria-label={t('finance.nextMonth')}>›</Button>
      {!isNow && (
        <Button variant="secondary" className="ml-1 px-3 py-1 text-xs" onClick={() => onChange(monthKey())}>
          {t('finance.today')}
        </Button>
      )}
    </div>
  );
}
