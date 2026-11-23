import { useState, type FormEvent } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import { Alert, Button, Input } from '@/components/ui';
import { ConfirmDialog, InlineLoader, Modal } from '@/components/ui/overlay';
import { fieldErrors, translateError } from '@/lib/errors';
import { formatDate, formatMoney } from '@/lib/format';
import { formatMonth } from '@/lib/month';
import { useAccounts, useCards } from '@/features/accounts/useAccounts';
import { useFinanceInvalidation } from '@/features/months/useFinanceInvalidation';
import type { Occurrence } from '@/features/expenses/expensesApi';
import { AccountSelect, ConversionPreview } from './MovementFields';
import { ACCEPTED_FILES, MAX_FILES, paymentsApi, type Attachment, type Payment } from './paymentsApi';

const todayIso = () => {
  const d = new Date();
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
};

/** Pagar un gasto del mes (total o parcial) desde una cuenta, con observaciones y comprobantes. */
export function PayDialog({ occurrence, onClose }: { occurrence: Occurrence; onClose: () => void }) {
  const { t } = useTranslation();
  const qc = useQueryClient();
  const refresh = useFinanceInvalidation();
  const { active: accounts, isPending: accountsLoading } = useAccounts();
  const { byId } = useCards();
  const card = byId(occurrence.cardId);
  const key = ['payments', occurrence.id];
  const q = useQuery({ queryKey: key, queryFn: () => paymentsApi.list(occurrence.id!) });
  const data = q.data;
  const remaining = data?.remaining ?? Math.max(occurrence.expectedAmount - occurrence.paidAmount, 0);

  const [accountId, setAccountId] = useState('');
  // null = lo que falta pagar (se actualiza al registrar pagos) hasta que el usuario escribe otro monto.
  const [amountInput, setAmount] = useState<string | null>(null);
  const amount = amountInput ?? String(remaining);
  const [date, setDate] = useState(todayIso());
  const [rate, setRate] = useState('');
  const [notes, setNotes] = useState('');
  const [files, setFiles] = useState<File[]>([]);
  const [deleting, setDeleting] = useState<Payment | null>(null);
  const [uploadFailed, setUploadFailed] = useState<unknown>(null);
  const account = accounts.find((a) => a.id === (accountId || accounts[0]?.id));

  // El pago y los comprobantes son dos pedidos: si falla la subida el pago ya quedó registrado,
  // así que no se cierra el diálogo y se puede volver a adjuntar desde el pago (sin pagar dos veces).
  const pay = useMutation({
    mutationFn: async () => {
      const before = new Set((data?.payments ?? []).map((p) => p.id));
      const res = await paymentsApi.pay(occurrence.id!, { accountId: account!.id, amount, date, rate, notes });
      const created = res.payments.find((p) => !before.has(p.id));
      let failed: unknown = null;
      if (created && files.length > 0) {
        try {
          await paymentsApi.upload(created.id, files);
        } catch (e) {
          failed = e;
        }
      }
      return failed;
    },
    onSuccess: async (failed) => {
      await Promise.all([refresh(), qc.invalidateQueries({ queryKey: key })]);
      if (failed) {
        setUploadFailed(failed);
        setFiles([]);
        setAmount(null);
      } else {
        onClose();
      }
    },
  });
  const remove = useMutation({
    mutationFn: (p: Payment) => paymentsApi.remove(p.id),
    onSuccess: async () => {
      setDeleting(null);
      await Promise.all([refresh(), qc.invalidateQueries({ queryKey: key })]);
    },
  });

  const errors = pay.isError ? fieldErrors(pay.error) : {};
  const err = (k: string) => (errors[k] ? translateError(t, errors[k]) : undefined);
  const tooMany = files.length > MAX_FILES;
  const submit = (e: FormEvent) => {
    e.preventDefault();
    if (account && !tooMany) pay.mutate();
  };
  const title = t('payments.title', { name: occurrence.description, month: formatMonth(occurrence.month, true) });

  return (
    <Modal open onClose={onClose} title={title} wide>
      {q.isPending ? <InlineLoader label={t('common.loading')} /> : (
        <div className="space-y-5">
          <div className="grid grid-cols-3 gap-2 text-sm">
            <Figure label={t('payments.expected')} value={formatMoney(data?.expectedAmount ?? occurrence.expectedAmount, occurrence.currency)} />
            <Figure label={t('payments.paid')} value={formatMoney(data?.paidAmount ?? occurrence.paidAmount, occurrence.currency)} />
            <Figure label={t('payments.remaining')} value={formatMoney(remaining, occurrence.currency)} strong />
          </div>
          {card && <p className="text-xs text-slate-500">{t('payments.withCard', { card: card.name })}</p>}

          {uploadFailed != null && (
            <Alert>{t('payments.uploadFailed', { reason: translateError(t, uploadFailed) })}</Alert>
          )}
          {(data?.payments.length ?? 0) > 0 && (
            <section>
              <h3 className="mb-2 text-sm font-semibold">{t('payments.history')}</h3>
              <ul className="divide-y divide-slate-100 rounded-lg border border-slate-200 dark:divide-slate-800 dark:border-slate-800">
                {data!.payments.map((p) => <PaymentRow key={p.id} p={p} onDelete={() => setDeleting(p)} />)}
              </ul>
            </section>
          )}

          {remaining > 0 || (data?.payments.length ?? 0) === 0 ? (
            <form onSubmit={submit} className="space-y-4" noValidate>
              <h3 className="text-sm font-semibold">{t('payments.new')}</h3>
              {pay.isError && <Alert>{translateError(t, pay.error)}</Alert>}
              {accountsLoading ? <InlineLoader label={t('common.loading')} /> : (
                <AccountSelect accounts={accounts} value={account?.id ?? ''} onChange={setAccountId}
                  label={t('payments.fromAccount')} error={err('accountId')} />
              )}
              <div className="grid gap-3 sm:grid-cols-2">
                <Input label={t('payments.amount', { currency: occurrence.currency })} name="amount" type="number"
                  inputMode="decimal" step="0.01" min="0.01" required value={amount} error={err('amount')}
                  onChange={(e) => setAmount(e.target.value)} />
                <Input label={t('payments.date')} name="date" type="date" required value={date} error={err('date')}
                  onChange={(e) => setDate(e.target.value)} />
              </div>
              <ConversionPreview amount={amount} from={occurrence.currency} to={account?.currency}
                purpose={occurrence.cardId ? 'PAYMENT_CARD' : 'PAYMENT'} rate={rate} onRate={setRate} />
              <Input label={t('payments.notes')} name="notes" maxLength={500} value={notes} error={err('notes')}
                onChange={(e) => setNotes(e.target.value)} />
              <label className="block space-y-1 text-sm">
                <span className="font-medium text-slate-700 dark:text-slate-300">{t('payments.files')}</span>
                <input type="file" multiple accept={ACCEPTED_FILES} onChange={(e) => setFiles(Array.from(e.target.files ?? []))}
                  className="block w-full text-sm file:mr-3 file:rounded-md file:border-0 file:bg-slate-100 file:px-3 file:py-1.5 dark:file:bg-slate-800" />
                <span className={tooMany ? 'text-xs text-red-600' : 'text-xs text-slate-500'}>
                  {tooMany ? t('payments.tooManyFiles', { max: MAX_FILES }) : t('payments.filesHint', { max: MAX_FILES })}
                </span>
              </label>
              <div className="flex justify-end gap-2">
                <Button type="button" variant="secondary" onClick={onClose}>{t('common.cancel')}</Button>
                <Button type="submit" loading={pay.isPending} disabled={!account || tooMany}>{t('payments.submit')}</Button>
              </div>
            </form>
          ) : (
            <div className="flex justify-end"><Button variant="secondary" onClick={onClose}>{t('common.close')}</Button></div>
          )}
        </div>
      )}
      <ConfirmDialog
        open={!!deleting}
        title={t('payments.deleteTitle')}
        message={t('payments.deleteMessage', { amount: deleting ? formatMoney(deleting.amount, deleting.currency) : '' })}
        confirmLabel={t('common.delete')}
        loading={remove.isPending}
        onConfirm={() => deleting && remove.mutate(deleting)}
        onClose={() => setDeleting(null)}
      />
    </Modal>
  );
}

function Figure({ label, value, strong }: { label: string; value: string; strong?: boolean }) {
  return (
    <div className="rounded-lg bg-slate-50 p-2 dark:bg-slate-800/50">
      <p className="text-xs text-slate-500">{label}</p>
      <p className={strong ? 'font-semibold tabular-nums' : 'tabular-nums'}>{value}</p>
    </div>
  );
}

function PaymentRow({ p, onDelete }: { p: Payment; onDelete: () => void }) {
  const { t } = useTranslation();
  const qc = useQueryClient();
  const removeFile = useMutation({
    mutationFn: (a: Attachment) => paymentsApi.removeAttachment(a.id),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['payments', p.occurrenceId] }),
  });
  const addFiles = useMutation({
    mutationFn: (files: File[]) => paymentsApi.upload(p.id, files),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['payments', p.occurrenceId] }),
  });
  return (
    <li className="space-y-1 p-3 text-sm">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <div>
          <span className="font-medium tabular-nums">{formatMoney(p.amount, p.currency)}</span>
          <span className="ml-2 text-xs text-slate-500">{formatDate(p.paidOn)}</span>
        </div>
        <div className="flex items-center gap-1">
          {!p.assumed && p.attachments.length < MAX_FILES && (
            <label className="cursor-pointer rounded-md px-2 py-1 text-xs text-brand-700 hover:bg-slate-100 dark:text-brand-500 dark:hover:bg-slate-800">
              {addFiles.isPending ? t('common.loading') : t('payments.attach')}
              <input type="file" multiple accept={ACCEPTED_FILES} className="sr-only"
                onChange={(e) => { const f = Array.from(e.target.files ?? []); if (f.length) addFiles.mutate(f); e.target.value = ''; }} />
            </label>
          )}
          <Button variant="ghost" className="px-2 py-1 text-xs text-red-600" onClick={onDelete}>{t('common.delete')}</Button>
        </div>
      </div>
      {addFiles.isError && <p className="text-xs text-red-600">{translateError(t, addFiles.error)}</p>}
      <p className="text-xs text-slate-500">
        {p.assumed ? t('payments.assumed') : t('payments.fromAccountShort', { account: p.accountName ?? '' })}
        {p.accountCurrency && p.accountCurrency !== p.currency && p.accountAmount != null && (
          <> · {formatMoney(p.accountAmount, p.accountCurrency)} ({t(`rates.type.${p.rateType ?? 'MANUAL'}`)} {formatMoney(p.rate ?? 0, 'ARS')})</>
        )}
      </p>
      {p.notes && <p className="text-xs italic text-slate-500">{p.notes}</p>}
      {p.attachments.length > 0 && (
        <ul className="flex flex-wrap gap-2 pt-1">
          {p.attachments.map((a) => (
            <li key={a.id} className="flex items-center gap-1 rounded-md border border-slate-200 px-2 py-1 text-xs dark:border-slate-700">
              <button type="button" className="text-brand-700 hover:underline dark:text-brand-500" onClick={() => void paymentsApi.open(a)}>
                {a.fileName}
              </button>
              <button type="button" aria-label={t('payments.removeFile', { name: a.fileName })} className="text-slate-400 hover:text-red-600"
                onClick={() => removeFile.mutate(a)}>×</button>
            </li>
          ))}
        </ul>
      )}
    </li>
  );
}
