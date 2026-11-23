import { useState, type FormEvent } from 'react';
import { useMutation } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import clsx from 'clsx';
import { Alert, Button, Input, Select } from '@/components/ui';
import { Modal } from '@/components/ui/overlay';
import { AdjustmentFields, adjustmentPayload, adjustmentState, type AdjustmentState } from '@/components/finance/AdjustmentFields';
import { fieldErrors, translateError } from '@/lib/errors';
import { formatMoney, type Currency } from '@/lib/format';
import { monthKey, monthsBetween } from '@/lib/month';
import { useCards } from '@/features/accounts/useAccounts';
import { useAuthStore } from '@/stores/authStore';
import { useCategories } from '@/features/categories/useCategories';
import { useFinanceInvalidation } from '@/features/months/useFinanceInvalidation';
import { expensesApi, type Expense, type ExpenseKind } from './expensesApi';

const KINDS: ExpenseKind[] = ['ONE_TIME', 'INSTALLMENTS', 'RECURRING'];

type FormState = {
  description: string;
  categoryId: string;
  kind: ExpenseKind;
  currency: Currency;
  amount: string;
  startMonth: string;
  installments: string;
  dueMode: 'last' | 'day';
  dueDay: string;
  variableAmount: boolean;
  adjust: AdjustmentState;
  notes: string;
  cardId: string;
  /** Solo si empieza antes del mes actual: sin valor por defecto, hay que elegir. */
  pastMonthsPaid: '' | 'yes' | 'no';
};

function initialState(expense: Expense | undefined, baseCurrency: Currency, defaultCategory: string): FormState {
  return {
    description: expense?.description ?? '',
    categoryId: expense?.categoryId ?? defaultCategory,
    kind: expense?.kind ?? 'RECURRING',
    currency: expense?.currency ?? baseCurrency,
    amount: expense ? String(expense.currentAmount) : '',
    startMonth: expense?.startMonth ?? monthKey(),
    installments: expense?.installments ? String(expense.installments) : '3',
    dueMode: expense?.dueDay ? 'day' : 'last',
    dueDay: expense?.dueDay ? String(expense.dueDay) : '10',
    variableAmount: expense?.variableAmount ?? false,
    adjust: adjustmentState(expense),
    notes: expense?.notes ?? '',
    cardId: expense?.cardId ?? '',
    pastMonthsPaid: '',
  };
}

/**
 * Alta y edición de un gasto. En edición no se cambian tipo, moneda, inicio, cuotas ni monto
 * (el monto cambia con "Registrar aumento" para conservar el historial).
 */
export function ExpenseForm({ expense, onClose }: { expense?: Expense; onClose: () => void }) {
  const { t } = useTranslation();
  const user = useAuthStore((s) => s.user);
  const { active, label } = useCategories();
  const { active: cards } = useCards();
  const refresh = useFinanceInvalidation();
  const editing = !!expense;
  // Por defecto "Otros": evita cargar sin querer un gasto como alquiler.
  const defaultCategory = (active.find((c) => c.systemKey === 'other') ?? active[0])?.id ?? '';
  const [f, setF] = useState<FormState>(() =>
    initialState(expense, user?.baseCurrency ?? 'ARS', defaultCategory),
  );
  const set = <K extends keyof FormState>(k: K, v: FormState[K]) => setF((s) => ({ ...s, [k]: v }));

  const startsInPast = !editing && f.startMonth < monthKey();
  const pastCount = startsInPast ? monthsBetween(f.startMonth, monthKey()) : 0;
  const [pastMissing, setPastMissing] = useState(false);

  const save = useMutation({
    mutationFn: async () => {
      const dueDay = f.dueMode === 'day' ? Number(f.dueDay) : null;
      const adjustment = adjustmentPayload({ ...f.adjust, enabled: f.kind === 'RECURRING' && f.adjust.enabled });
      if (editing) {
        return expensesApi.update(expense.id, {
          description: f.description,
          categoryId: f.categoryId,
          dueDay: dueDay ?? 0,
          ...(f.cardId ? { cardId: f.cardId } : { clearCard: true }),
          variableAmount: f.variableAmount,
          notes: f.notes,
          ...(expense.kind === 'RECURRING'
            ? adjustment.adjustEveryMonths != null
              ? {
                  adjustEveryMonths: adjustment.adjustEveryMonths,
                  adjustWithInflation: adjustment.adjustWithInflation,
                  ...(adjustment.adjustPercent != null ? { adjustPercent: adjustment.adjustPercent } : {}),
                }
              : { clearAdjustment: true }
            : {}),
        });
      }
      return expensesApi.create({
        description: f.description,
        categoryId: f.categoryId || defaultCategory,
        kind: f.kind,
        currency: f.currency,
        amount: f.amount,
        startMonth: f.startMonth,
        installments: f.kind === 'INSTALLMENTS' ? Number(f.installments) : null,
        dueDay,
        variableAmount: f.kind === 'RECURRING' && f.variableAmount,
        ...adjustment,
        notes: f.notes || null,
        cardId: f.cardId || null,
        pastMonthsPaid: startsInPast ? f.pastMonthsPaid === 'yes' : null,
      });
    },
    onSuccess: async () => {
      await refresh();
      onClose();
    },
  });

  const errors = save.isError ? fieldErrors(save.error) : {};
  const err = (k: string) => (errors[k] ? translateError(t, errors[k]) : undefined);
  const total = f.kind === 'INSTALLMENTS' && Number(f.amount) > 0 && Number(f.installments) > 0
    ? Number(f.amount) * Number(f.installments)
    : null;

  const submit = (e: FormEvent) => {
    e.preventDefault();
    if (startsInPast && f.pastMonthsPaid === '') {
      setPastMissing(true);
      return;
    }
    save.mutate();
  };

  return (
    <Modal open onClose={onClose} title={editing ? t('expenses.editTitle') : t('expenses.newTitle')} wide>
      <form onSubmit={submit} className="space-y-4" noValidate>
        {save.isError && <Alert>{translateError(t, save.error)}</Alert>}

        {!editing && (
          <fieldset>
            <legend className="mb-1 text-sm font-medium text-slate-700 dark:text-slate-300">{t('expenses.fields.kind')}</legend>
            <div className="grid grid-cols-3 gap-2">
              {KINDS.map((k) => (
                <button
                  key={k}
                  type="button"
                  aria-pressed={f.kind === k}
                  onClick={() => set('kind', k)}
                  className={clsx(
                    'rounded-lg border px-2 py-2 text-left text-sm transition',
                    f.kind === k
                      ? 'border-brand-700 bg-brand-50 text-brand-800 dark:bg-brand-800/30 dark:text-brand-100'
                      : 'border-slate-300 hover:bg-slate-50 dark:border-slate-700 dark:hover:bg-slate-800',
                  )}
                >
                  <span className="block font-medium">{t(`expenses.kind.${k}`)}</span>
                  <span className="block text-xs text-slate-500">{t(`expenses.kindHint.${k}`)}</span>
                </button>
              ))}
            </div>
          </fieldset>
        )}

        <div className="grid gap-4 sm:grid-cols-2">
          <Input label={t('expenses.fields.description')} name="description" required maxLength={120}
            value={f.description} error={err('description')} onChange={(e) => set('description', e.target.value)} />
          <Select label={t('expenses.fields.category')} name="categoryId" value={f.categoryId || defaultCategory}
            onChange={(e) => set('categoryId', e.target.value)}>
            {active.map((c) => (
              <option key={c.id} value={c.id}>{label(c)}</option>
            ))}
          </Select>

          {!editing && (
            <>
              <Input
                label={f.kind === 'INSTALLMENTS' ? t('expenses.fields.amountPerInstallment') : f.variableAmount ? t('expenses.fields.estimatedAmount') : t('expenses.fields.amount')}
                name="amount" type="number" inputMode="decimal" step="0.01" min="0.01" required
                value={f.amount} error={err('amount')} onChange={(e) => set('amount', e.target.value)} />
              <Select label={t('finance.currency')} name="currency" value={f.currency}
                onChange={(e) => set('currency', e.target.value as Currency)}>
                <option value="ARS">ARS</option>
                <option value="USD">USD</option>
              </Select>
              <Input
                label={f.kind === 'INSTALLMENTS' ? t('expenses.fields.firstInstallment') : f.kind === 'ONE_TIME' ? t('expenses.fields.month') : t('expenses.fields.since')}
                name="startMonth" type="month" required
                value={f.startMonth} error={err('startMonth')} onChange={(e) => set('startMonth', e.target.value)} />
              {f.kind === 'INSTALLMENTS' && (
                <Input label={t('expenses.fields.installments')} name="installments" type="number" min="1" max="360" required
                  value={f.installments} error={err('installments')}
                  hint={total ? t('expenses.totalHint', { total: formatMoney(total, f.currency) }) : undefined}
                  onChange={(e) => set('installments', e.target.value)} />
              )}
            </>
          )}
        </div>

        <fieldset className="space-y-2">
          <legend className="text-sm font-medium text-slate-700 dark:text-slate-300">{t('expenses.fields.dueDate')}</legend>
          <div className="flex flex-wrap items-center gap-4 text-sm">
            <label className="flex items-center gap-2">
              <input type="radio" name="dueMode" checked={f.dueMode === 'last'} onChange={() => set('dueMode', 'last')} />
              {t('expenses.dueLastDay')}
            </label>
            <label className="flex items-center gap-2">
              <input type="radio" name="dueMode" checked={f.dueMode === 'day'} onChange={() => set('dueMode', 'day')} />
              {t('expenses.dueOnDay')}
            </label>
            {f.dueMode === 'day' && (
              <input type="number" min={1} max={31} aria-label={t('expenses.fields.dueDay')} value={f.dueDay}
                onChange={(e) => set('dueDay', e.target.value)}
                className="w-20 rounded-lg border border-slate-300 bg-white px-2 py-1.5 text-sm dark:border-slate-700 dark:bg-slate-900" />
            )}
          </div>
        </fieldset>

        {(editing ? expense.kind : f.kind) === 'RECURRING' && (
          <div className="space-y-3 rounded-lg bg-slate-50 p-3 dark:bg-slate-800/50">
            <label className="flex items-start gap-2 text-sm">
              <input type="checkbox" className="mt-0.5" checked={f.variableAmount} onChange={(e) => set('variableAmount', e.target.checked)} />
              <span>
                {t('expenses.variableLabel')}
                <span className="block text-xs text-slate-500">{t('expenses.variableHint')}</span>
              </span>
            </label>
            <AdjustmentFields value={f.adjust} onChange={(v) => set('adjust', v)}
              currency={editing ? expense.currency : f.currency}
              label={t('expenses.adjustLabel')} hint={t('expenses.adjustHint')}
              errors={{ adjustEveryMonths: err('adjustEveryMonths'), adjustPercent: err('adjustPercent') }} />
          </div>
        )}

        {cards.length > 0 && (
          <Select label={t('cards.field')} name="cardId" value={f.cardId} onChange={(e) => set('cardId', e.target.value)}>
            <option value="">{t('cards.none')}</option>
            {cards.map((c) => <option key={c.id} value={c.id}>{c.name}</option>)}
          </Select>
        )}

        {startsInPast && (
          <fieldset className={clsx('space-y-2 rounded-lg p-3 text-sm',
            pastMissing || err('pastMonthsPaid') ? 'bg-red-50 dark:bg-red-900/20' : 'bg-amber-50 dark:bg-amber-900/20')}>
            <legend className="sr-only">{t('expenses.pastMonths.question', { count: pastCount })}</legend>
            <p className="font-medium">{t('expenses.pastMonths.question', { count: pastCount })}</p>
            <p className="text-xs text-slate-600 dark:text-slate-300">{t('expenses.pastMonths.hint')}</p>
            {(['yes', 'no'] as const).map((v) => (
              <label key={v} className="flex items-center gap-2">
                <input type="radio" name="pastMonthsPaid" checked={f.pastMonthsPaid === v}
                  onChange={() => { set('pastMonthsPaid', v); setPastMissing(false); }} />
                {t(`expenses.pastMonths.${v}`)}
              </label>
            ))}
            {(pastMissing || err('pastMonthsPaid')) && <p className="text-xs text-red-600">{t('errors.expense.past_months_choice_required')}</p>}
          </fieldset>
        )}

        <Input label={t('expenses.fields.notes')} name="notes" maxLength={500}
          value={f.notes} onChange={(e) => set('notes', e.target.value)} />

        <div className="flex justify-end gap-2 pt-2">
          <Button type="button" variant="secondary" onClick={onClose}>{t('common.cancel')}</Button>
          <Button type="submit" loading={save.isPending}>{t('common.save')}</Button>
        </div>
      </form>
    </Modal>
  );
}
