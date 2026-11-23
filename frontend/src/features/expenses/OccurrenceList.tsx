import { useState, type FormEvent } from 'react';
import { useMutation } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import { Alert, Button, Input } from '@/components/ui';
import { Badge, Modal } from '@/components/ui/overlay';
import { translateError } from '@/lib/errors';
import { useFinanceInvalidation } from '@/features/months/useFinanceInvalidation';
import { formatMoney } from '@/lib/format';
import { useCards } from '@/features/accounts/useAccounts';
import { PayDialog } from '@/features/payments/PayDialog';
import { expensesApi, type Occurrence } from './expensesApi';
import { CategoryChip, DueText, InstallmentText, Money, StatusBadge } from './parts';

/**
 * Lista de gastos a pagar (vencimientos). En móvil cada ítem es una tarjeta.
 * {@code groupByCard}: los gastos con tarjeta se agrupan debajo de su tarjeta (cada cuota se paga por separado).
 */
export function OccurrenceList({ items, compact = false, groupByCard = false }: { items: Occurrence[]; compact?: boolean; groupByCard?: boolean }) {
  const { t } = useTranslation();
  const { byId } = useCards();
  const [editing, setEditing] = useState<Occurrence | null>(null);
  const [paying, setPaying] = useState<Occurrence | null>(null);

  const withCard = groupByCard ? items.filter((o) => o.cardId && byId(o.cardId)) : [];
  const plain = groupByCard ? items.filter((o) => !withCard.includes(o)) : items;
  const cardIds = [...new Set(withCard.map((o) => o.cardId!))];

  const list = (rows: Occurrence[]) => (
      <ul className="divide-y divide-slate-100 dark:divide-slate-800">
        {rows.map((o) => (
          <li key={o.id ?? `${o.expenseId}-${o.month}`} className="flex flex-col gap-2 py-3 sm:flex-row sm:items-center sm:gap-4">
            <div className="min-w-0 flex-1">
              <div className="flex flex-wrap items-center gap-2">
                <span className="truncate font-medium">{o.description}</span>
                <InstallmentText o={o} />
                {o.variableAmount && !compact && <span className="text-xs text-slate-500">{t('expenses.variable')}</span>}
              </div>
              <div className="mt-1 flex flex-wrap items-center gap-x-3 gap-y-1 text-xs text-slate-500">
                <CategoryChip id={o.categoryId} />
                {!groupByCard && byId(o.cardId) && <CardChip name={byId(o.cardId)!.name} color={byId(o.cardId)!.color} />}
                <DueText o={o} />
              </div>
              {o.notes && !compact && <p className="mt-1 text-xs text-slate-500 italic">{o.notes}</p>}
            </div>
            <div className="flex items-center justify-between gap-3 sm:justify-end">
              <div className="text-right">
                <Money value={o.expectedAmount} currency={o.currency} className="font-semibold tabular-nums" />
                <div className="mt-0.5">
                  {o.projected ? <Badge tone="info">{t('expenses.status.PROJECTED')}</Badge> : <StatusBadge status={o.status} />}
                </div>
              </div>
              {!o.projected && (
                <div className="flex gap-1">
                  {!compact && (
                    <Button variant="ghost" className="px-2" onClick={() => setEditing(o)} aria-label={t('common.edit')}>
                      <svg viewBox="0 0 24 24" className="size-4" fill="none" stroke="currentColor" strokeWidth="2" aria-hidden>
                        <path d="M4 20h4L19 9l-4-4L4 16zM14 6l4 4" />
                      </svg>
                    </Button>
                  )}
                  {o.status !== 'PAID' ? (
                    <Button variant="secondary" onClick={() => setPaying(o)}>{t('expenses.pay')}</Button>
                  ) : !compact && (
                    <Button variant="ghost" className="px-2 text-xs" onClick={() => setPaying(o)}>{t('payments.view')}</Button>
                  )}
                </div>
              )}
            </div>
          </li>
        ))}
      </ul>
  );

  return (
    <>
      {plain.length > 0 && list(plain)}
      {cardIds.map((id) => {
        const card = byId(id)!;
        const rows = withCard.filter((o) => o.cardId === id);
        const pending = rows.reduce((s, o) => s + Math.max(o.expectedAmount - o.paidAmount, 0), 0);
        const currencies = [...new Set(rows.map((o) => o.currency))];
        return (
          <section key={id} className="mt-3 rounded-lg border border-slate-200 px-3 dark:border-slate-800">
            <header className="flex flex-wrap items-center justify-between gap-2 border-b border-slate-100 py-2 dark:border-slate-800">
              <span className="flex items-center gap-2 text-sm font-semibold">
                <span className="size-2.5 rounded-sm" style={{ backgroundColor: card.color }} aria-hidden />
                {card.name}
                <span className="font-normal text-slate-500">· {t('cards.items', { count: rows.length })}</span>
              </span>
              {currencies.length === 1 && (
                <span className="text-xs text-slate-500">{t('cards.pending', { amount: formatMoney(pending, currencies[0]!) })}</span>
              )}
            </header>
            {list(rows)}
          </section>
        );
      })}
      {editing && <OccurrenceEditDialog occurrence={editing} onClose={() => setEditing(null)} />}
      {paying && <PayDialog occurrence={paying} onClose={() => setPaying(null)} />}
    </>
  );
}

function CardChip({ name, color }: { name: string; color: string }) {
  return (
    <span className="inline-flex items-center gap-1">
      <span className="size-2 rounded-sm" style={{ backgroundColor: color }} aria-hidden />
      {name}
    </span>
  );
}

function OccurrenceEditDialog({ occurrence, onClose }: { occurrence: Occurrence; onClose: () => void }) {
  const { t } = useTranslation();
  const refresh = useFinanceInvalidation();
  const [form, setForm] = useState({
    expectedAmount: String(occurrence.expectedAmount),
    dueDate: occurrence.dueDate,
    notes: occurrence.notes ?? '',
  });
  const save = useMutation({
    mutationFn: () => expensesApi.updateOccurrence(occurrence.id!, form),
    onSuccess: async () => {
      await refresh();
      onClose();
    },
  });
  const submit = (e: FormEvent) => {
    e.preventDefault();
    save.mutate();
  };
  return (
    <Modal open onClose={onClose} title={t('expenses.editOccurrence', { name: occurrence.description })}>
      <form onSubmit={submit} className="space-y-4">
        {save.isError && <Alert>{translateError(t, save.error)}</Alert>}
        <p className="text-sm text-slate-500">{t('expenses.editOccurrenceHint')}</p>
        <Input label={t('expenses.fields.amount')} name="expectedAmount" type="number" inputMode="decimal" step="0.01" min="0.01" required
          value={form.expectedAmount} onChange={(e) => setForm({ ...form, expectedAmount: e.target.value })} />
        <Input label={t('expenses.fields.dueDate')} name="dueDate" type="date" required
          value={form.dueDate} onChange={(e) => setForm({ ...form, dueDate: e.target.value })} />
        <Input label={t('expenses.fields.notes')} name="notes" maxLength={500}
          value={form.notes} onChange={(e) => setForm({ ...form, notes: e.target.value })} />
        <div className="flex justify-end gap-2">
          <Button type="button" variant="secondary" onClick={onClose}>{t('common.cancel')}</Button>
          <Button type="submit" loading={save.isPending}>{t('common.save')}</Button>
        </div>
      </form>
    </Modal>
  );
}
