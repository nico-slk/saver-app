import { useState, type FormEvent } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import { Alert, Button, Input } from '@/components/ui';
import { InlineLoader, Modal } from '@/components/ui/overlay';
import { fieldErrors, translateError } from '@/lib/errors';
import { formatDate, formatMoney } from '@/lib/format';
import { formatMonth, type MonthKey } from '@/lib/month';
import { useAccounts } from '@/features/accounts/useAccounts';
import { useFinanceInvalidation } from '@/features/months/useFinanceInvalidation';
import { AccountSelect, ConversionPreview } from '@/features/payments/MovementFields';
import { incomesApi, type Income } from './incomesApi';

/** Cobrar un ingreso del mes (total o en partes) en una cuenta. */
export function ReceiveDialog({ income, month, expected, onClose }: { income: Income; month: MonthKey; expected: number; onClose: () => void }) {
  const { t } = useTranslation();
  const qc = useQueryClient();
  const refresh = useFinanceInvalidation();
  const { active: accounts, isPending: loadingAccounts } = useAccounts();
  const key = ['incomes', 'receipts', income.id, month];
  const q = useQuery({ queryKey: key, queryFn: () => incomesApi.receipts(income.id, month) });
  const received = (q.data ?? []).reduce((s, r) => s + r.amount, 0);
  const pending = Math.max(expected - received, 0);

  const [accountId, setAccountId] = useState('');
  const [amount, setAmount] = useState('');
  const [date, setDate] = useState(new Date().toISOString().slice(0, 10));
  const [rate, setRate] = useState('');
  const [notes, setNotes] = useState('');
  const account = accounts.find((a) => a.id === (accountId || accounts[0]?.id));
  const value = amount === '' ? String(pending || expected) : amount;

  const save = useMutation({
    mutationFn: () => incomesApi.receive(income.id, { month, accountId: account!.id, amount: value, date, rate, notes }),
    onSuccess: async () => {
      await Promise.all([refresh(), qc.invalidateQueries({ queryKey: key })]);
      onClose();
    },
  });
  const remove = useMutation({
    mutationFn: (id: string) => incomesApi.removeReceipt(id),
    onSuccess: () => Promise.all([refresh(), qc.invalidateQueries({ queryKey: key })]),
  });
  const errors = save.isError ? fieldErrors(save.error) : {};
  const err = (k: string) => (errors[k] ? translateError(t, errors[k]) : undefined);
  const submit = (e: FormEvent) => { e.preventDefault(); if (account) save.mutate(); };

  return (
    <Modal open onClose={onClose} title={t('receipts.title', { name: income.description, month: formatMonth(month, true) })}>
      {q.isPending ? <InlineLoader label={t('common.loading')} /> : (
        <div className="space-y-4">
          <p className="text-sm">
            {t('receipts.summary', { received: formatMoney(received, income.currency), expected: formatMoney(expected, income.currency) })}
          </p>
          {(q.data?.length ?? 0) > 0 && (
            <ul className="divide-y divide-slate-100 rounded-lg border border-slate-200 text-sm dark:divide-slate-800 dark:border-slate-800">
              {q.data!.map((r) => (
                <li key={r.id} className="flex items-center justify-between gap-2 p-2.5">
                  <div>
                    <span className="font-medium tabular-nums">{formatMoney(r.amount, r.currency)}</span>
                    <span className="ml-2 text-xs text-slate-500">{formatDate(r.receivedOn)} · {r.accountName}</span>
                    {r.accountCurrency && r.accountCurrency !== r.currency && (
                      <span className="block text-xs text-slate-500">{formatMoney(r.accountAmount, r.accountCurrency)}</span>
                    )}
                  </div>
                  <Button variant="ghost" className="px-2 py-1 text-xs text-red-600" loading={remove.isPending}
                    onClick={() => remove.mutate(r.id)}>{t('common.delete')}</Button>
                </li>
              ))}
            </ul>
          )}
          <form onSubmit={submit} className="space-y-4" noValidate>
            {save.isError && <Alert>{translateError(t, save.error)}</Alert>}
            {loadingAccounts ? <InlineLoader label={t('common.loading')} /> : (
              <AccountSelect accounts={accounts} value={account?.id ?? ''} onChange={setAccountId} label={t('receipts.toAccount')} error={err('accountId')} />
            )}
            <div className="grid grid-cols-2 gap-3">
              <Input label={t('payments.amount', { currency: income.currency })} name="amount" type="number" inputMode="decimal"
                step="0.01" min="0.01" value={value} error={err('amount')} onChange={(e) => setAmount(e.target.value)} />
              <Input label={t('payments.date')} name="date" type="date" value={date} onChange={(e) => setDate(e.target.value)} />
            </div>
            <ConversionPreview amount={value} from={income.currency} to={account?.currency} purpose="RECEIPT" rate={rate} onRate={setRate} />
            <Input label={t('payments.notes')} name="notes" maxLength={500} value={notes} onChange={(e) => setNotes(e.target.value)} />
            <div className="flex justify-end gap-2">
              <Button type="button" variant="secondary" onClick={onClose}>{t('common.cancel')}</Button>
              <Button type="submit" loading={save.isPending} disabled={!account}>{t('receipts.submit')}</Button>
            </div>
          </form>
        </div>
      )}
    </Modal>
  );
}
