import { useState, type FormEvent } from 'react';
import { useMutation, useQuery } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import clsx from 'clsx';
import { Alert, Button, Card, Input, Select } from '@/components/ui';
import { Badge, ConfirmDialog, EmptyState, InlineLoader, Modal } from '@/components/ui/overlay';
import { AmountChangeDialog, EndDialog } from '@/components/finance/AmountChangeDialog';
import { AdjustmentFields, adjustmentPayload, adjustmentState, useAdjustmentSummary } from '@/components/finance/AdjustmentFields';
import { fieldErrors, translateError } from '@/lib/errors';
import { formatMoney, type Currency } from '@/lib/format';
import { formatMonth, monthKey } from '@/lib/month';
import { useAuthStore } from '@/stores/authStore';
import { queryKeys } from '@/features/months/monthsApi';
import { useFinanceInvalidation } from '@/features/months/useFinanceInvalidation';
import { incomesApi, type Income, type IncomeKind } from './incomesApi';

export function IncomesPage() {
  const { t } = useTranslation();
  const q = useQuery({ queryKey: queryKeys.incomes, queryFn: incomesApi.list });
  const refresh = useFinanceInvalidation();
  const [creating, setCreating] = useState(false);
  const [raising, setRaising] = useState<Income | null>(null);
  const [ending, setEnding] = useState<Income | null>(null);
  const [adjusting, setAdjusting] = useState<Income | null>(null);
  const ruleSummary = useAdjustmentSummary();
  const [deleting, setDeleting] = useState<Income | null>(null);
  const remove = useMutation({
    mutationFn: (id: string) => incomesApi.remove(id),
    onSuccess: async () => {
      setDeleting(null);
      await refresh();
    },
  });

  const list = q.data ?? [];
  const now = monthKey();
  const ended = (i: Income) => !!i.endMonth && i.endMonth < now;
  const groups: { key: string; items: Income[] }[] = [
    { key: 'fixed', items: list.filter((i) => i.kind === 'FIXED' && !ended(i)) },
    { key: 'recurring', items: list.filter((i) => i.kind === 'EXTRA' && i.recurring && !ended(i)) },
    { key: 'oneOff', items: list.filter((i) => i.kind === 'EXTRA' && !i.recurring && !ended(i)) },
    { key: 'finished', items: list.filter(ended) },
  ];

  const describe = (i: Income) => {
    if (!i.recurring) return t('incomes.onlyIn', { month: formatMonth(i.startMonth) });
    if (!i.endMonth) return t('incomes.since', { month: formatMonth(i.startMonth, true) });
    return t('incomes.range', { from: formatMonth(i.startMonth, true), to: formatMonth(i.endMonth, true) });
  };

  return (
    <div className="space-y-5">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <h1 className="text-2xl font-bold">{t('nav.incomes')}</h1>
        <Button onClick={() => setCreating(true)}>+ {t('incomes.new')}</Button>
      </div>
      <p className="text-sm text-slate-500">{t('incomes.intro')}</p>
      {remove.isError && <Alert>{translateError(t, remove.error)}</Alert>}
      {q.isPending && <InlineLoader label={t('common.loading')} />}
      {q.isSuccess && list.length === 0 && (
        <EmptyState title={t('incomes.empty')} action={<Button onClick={() => setCreating(true)}>+ {t('incomes.new')}</Button>} />
      )}
      {groups.filter((g) => g.items.length > 0).map((g) => (
        <Card key={g.key} className={g.key === 'finished' ? 'opacity-80' : undefined}>
          <h2 className="mb-1 font-semibold">{t(`incomes.groups.${g.key}`)}</h2>
          <ul className="divide-y divide-slate-100 dark:divide-slate-800">
            {g.items.map((i) => (
              <li key={i.id} className="flex flex-col gap-2 py-3 sm:flex-row sm:items-center sm:gap-4">
                <div className="min-w-0 flex-1">
                  <div className="flex flex-wrap items-center gap-2">
                    <span className="font-medium">{i.description}</span>
                    <Badge tone={i.kind === 'FIXED' ? 'success' : 'info'}>{t(`incomes.kind.${i.kind}`)}</Badge>
                  </div>
                  <p className="mt-1 text-xs text-slate-500">{describe(i)}</p>
                  {i.recurring && !ended(i) && ruleSummary(i) && <p className="text-xs text-slate-500">{ruleSummary(i)}</p>}
                </div>
                <div className="flex items-center justify-between gap-2 sm:justify-end">
                  <span className="font-semibold tabular-nums">
                    {i.currentAmount != null ? formatMoney(i.currentAmount, i.currency) : '—'}
                  </span>
                  <div className="flex flex-wrap justify-end gap-1">
                    {i.recurring && !ended(i) && (
                      <Button variant="ghost" className="px-2 py-1 text-xs" onClick={() => setRaising(i)}>{t('incomes.changeAmount')}</Button>
                    )}
                    {i.recurring && !ended(i) && (
                      <Button variant="ghost" className="px-2 py-1 text-xs" onClick={() => setAdjusting(i)}>{t('incomes.increases')}</Button>
                    )}
                    {i.recurring && !i.endMonth && (
                      <Button variant="ghost" className="px-2 py-1 text-xs" onClick={() => setEnding(i)}>{t('incomes.end')}</Button>
                    )}
                    <Button variant="ghost" className="px-2 py-1 text-xs text-red-600" onClick={() => setDeleting(i)}>{t('common.delete')}</Button>
                  </div>
                </div>
              </li>
            ))}
          </ul>
        </Card>
      ))}
      {creating && <IncomeForm onClose={() => setCreating(false)} />}
      {raising && (
        <AmountChangeDialog
          title={t('incomes.changeAmountTitle', { name: raising.description })}
          hint={t('incomes.changeAmountHint')}
          currentAmount={raising.currentAmount}
          minMonth={raising.startMonth}
          onSubmit={(input) => incomesApi.changeAmount(raising.id, input)}
          onClose={() => setRaising(null)}
        />
      )}
      {ending && (
        <EndDialog
          title={t('incomes.endTitle', { name: ending.description })}
          hint={t('incomes.endHint')}
          minMonth={ending.startMonth}
          onSubmit={(endMonth) => incomesApi.update(ending.id, { endMonth })}
          onClose={() => setEnding(null)}
        />
      )}
      {adjusting && <IncomeAdjustmentDialog income={adjusting} onClose={() => setAdjusting(null)} />}
      <ConfirmDialog
        open={!!deleting}
        title={t('incomes.deleteTitle')}
        message={t('incomes.deleteMessage', { name: deleting?.description ?? '' })}
        confirmLabel={t('common.delete')}
        loading={remove.isPending}
        onConfirm={() => deleting && remove.mutate(deleting.id)}
        onClose={() => setDeleting(null)}
      />
    </div>
  );
}

type Repeat = 'once' | 'months' | 'indefinite';

function IncomeForm({ onClose }: { onClose: () => void }) {
  const { t } = useTranslation();
  const user = useAuthStore((s) => s.user);
  const refresh = useFinanceInvalidation();
  const [f, setF] = useState({
    description: '',
    kind: 'FIXED' as IncomeKind,
    currency: (user?.baseCurrency ?? 'ARS') as Currency,
    amount: '',
    startMonth: monthKey(),
    repeat: 'indefinite' as Repeat,
    months: '3',
    adjust: adjustmentState(),
  });
  const recurring = f.kind === 'FIXED' || f.repeat !== 'once';
  const set = <K extends keyof typeof f>(k: K, v: (typeof f)[K]) => setF((s) => ({ ...s, [k]: v }));
  const save = useMutation({
    mutationFn: () =>
      incomesApi.create({
        description: f.description,
        kind: f.kind,
        currency: f.currency,
        amount: f.amount,
        startMonth: f.startMonth,
        months: f.kind === 'FIXED' || f.repeat === 'indefinite' ? null : f.repeat === 'once' ? 1 : Number(f.months),
        ...adjustmentPayload({ ...f.adjust, enabled: recurring && f.adjust.enabled }),
      }),
    onSuccess: async () => {
      await refresh();
      onClose();
    },
  });
  const errors = save.isError ? fieldErrors(save.error) : {};
  const err = (k: string) => (errors[k] ? translateError(t, errors[k]) : undefined);
  const submit = (e: FormEvent) => {
    e.preventDefault();
    save.mutate();
  };

  return (
    <Modal open onClose={onClose} title={t('incomes.newTitle')}>
      <form onSubmit={submit} className="space-y-4" noValidate>
        {save.isError && <Alert>{translateError(t, save.error)}</Alert>}
        <div className="grid grid-cols-2 gap-2">
          {(['FIXED', 'EXTRA'] as IncomeKind[]).map((k) => (
            <button key={k} type="button" aria-pressed={f.kind === k}
              onClick={() => setF((s) => ({ ...s, kind: k, repeat: k === 'FIXED' ? 'indefinite' : 'once' }))}
              className={clsx('rounded-lg border px-3 py-2 text-left text-sm transition',
                f.kind === k
                  ? 'border-brand-700 bg-brand-50 text-brand-800 dark:bg-brand-800/30 dark:text-brand-100'
                  : 'border-slate-300 hover:bg-slate-50 dark:border-slate-700 dark:hover:bg-slate-800')}>
              <span className="block font-medium">{t(`incomes.kind.${k}`)}</span>
              <span className="block text-xs text-slate-500">{t(`incomes.kindHint.${k}`)}</span>
            </button>
          ))}
        </div>
        <Input label={t('incomes.fields.description')} name="description" required maxLength={120}
          placeholder={f.kind === 'FIXED' ? t('incomes.placeholderFixed') : t('incomes.placeholderExtra')}
          value={f.description} error={err('description')} onChange={(e) => set('description', e.target.value)} />
        <div className="grid grid-cols-2 gap-3">
          <Input label={t('incomes.fields.amount')} name="amount" type="number" inputMode="decimal" step="0.01" min="0.01" required
            value={f.amount} error={err('amount')} onChange={(e) => set('amount', e.target.value)} />
          <Select label={t('finance.currency')} name="currency" value={f.currency} onChange={(e) => set('currency', e.target.value as Currency)}>
            <option value="ARS">ARS</option>
            <option value="USD">USD</option>
          </Select>
        </div>
        <Input label={f.kind === 'FIXED' || f.repeat !== 'once' ? t('incomes.fields.since') : t('incomes.fields.month')}
          name="startMonth" type="month" required value={f.startMonth} onChange={(e) => set('startMonth', e.target.value)} />
        {f.kind === 'EXTRA' && (
          <fieldset className="space-y-2 rounded-lg bg-slate-50 p-3 text-sm dark:bg-slate-800/50">
            <legend className="sr-only">{t('incomes.fields.repeat')}</legend>
            {(['once', 'months', 'indefinite'] as Repeat[]).map((r) => (
              <label key={r} className="flex items-center gap-2">
                <input type="radio" name="repeat" checked={f.repeat === r} onChange={() => set('repeat', r)} />
                {t(`incomes.repeat.${r}`)}
                {r === 'months' && f.repeat === 'months' && (
                  <input type="number" min={2} max={600} aria-label={t('incomes.fields.months')} value={f.months}
                    onChange={(e) => set('months', e.target.value)}
                    className="w-20 rounded-lg border border-slate-300 bg-white px-2 py-1 dark:border-slate-700 dark:bg-slate-900" />
                )}
              </label>
            ))}
            <p className="text-xs text-slate-500">{t('incomes.repeatHint')}</p>
          </fieldset>
        )}
        {recurring && (
          <div className="rounded-lg bg-slate-50 p-3 dark:bg-slate-800/50">
            <AdjustmentFields value={f.adjust} onChange={(v) => set('adjust', v)} currency={f.currency}
              label={t('incomes.adjustLabel')} hint={t('incomes.adjustHint')}
              errors={{ adjustEveryMonths: err('adjustEveryMonths'), adjustPercent: err('adjustPercent') }} />
          </div>
        )}
        <div className="flex justify-end gap-2">
          <Button type="button" variant="secondary" onClick={onClose}>{t('common.cancel')}</Button>
          <Button type="submit" loading={save.isPending}>{t('common.save')}</Button>
        </div>
      </form>
    </Modal>
  );
}

/** Configura o quita la regla de aumentos de un ingreso existente. */
function IncomeAdjustmentDialog({ income, onClose }: { income: Income; onClose: () => void }) {
  const { t } = useTranslation();
  const refresh = useFinanceInvalidation();
  const [adjust, setAdjust] = useState(() => ({ ...adjustmentState(income), enabled: true }));
  const save = useMutation({
    mutationFn: (clear: boolean) => {
      if (clear) return incomesApi.update(income.id, { clearAdjustment: true });
      const p = adjustmentPayload(adjust);
      return incomesApi.update(income.id, {
        adjustEveryMonths: p.adjustEveryMonths,
        adjustWithInflation: p.adjustWithInflation,
        ...(p.adjustPercent != null ? { adjustPercent: p.adjustPercent } : {}),
      });
    },
    onSuccess: async () => {
      await refresh();
      onClose();
    },
  });
  const errors = save.isError ? fieldErrors(save.error) : {};
  const err = (k: string) => (errors[k] ? translateError(t, errors[k]) : undefined);
  return (
    <Modal open onClose={onClose} title={t('incomes.increasesTitle', { name: income.description })}>
      <form className="space-y-4" noValidate onSubmit={(e) => { e.preventDefault(); save.mutate(!adjust.enabled); }}>
        {save.isError && <Alert>{translateError(t, save.error)}</Alert>}
        <AdjustmentFields value={adjust} onChange={setAdjust} currency={income.currency}
          label={t('incomes.adjustLabel')} hint={t('incomes.adjustHint')}
          errors={{ adjustEveryMonths: err('adjustEveryMonths'), adjustPercent: err('adjustPercent') }} />
        <div className="flex justify-end gap-2">
          <Button type="button" variant="secondary" onClick={onClose}>{t('common.cancel')}</Button>
          <Button type="submit" loading={save.isPending}>{t('common.save')}</Button>
        </div>
      </form>
    </Modal>
  );
}
