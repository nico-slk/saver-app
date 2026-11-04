import { useTranslation } from 'react-i18next';
import { Badge } from '@/components/ui/overlay';
import { formatDate, formatMoney, type Currency } from '@/lib/format';
import { useCategories } from '@/features/categories/useCategories';
import type { Occurrence, OccurrenceStatus } from './expensesApi';

export function CategoryChip({ id }: { id: string }) {
  const { byId, label } = useCategories();
  const c = byId.get(id);
  return (
    <span className="inline-flex items-center gap-1.5 text-xs text-slate-600 dark:text-slate-300">
      <span className="size-2.5 shrink-0 rounded-full" style={{ backgroundColor: c?.color ?? '#94a3b8' }} aria-hidden />
      <span className={c?.deleted ? 'line-through decoration-slate-400' : undefined}>{label(c)}</span>
    </span>
  );
}

const STATUS_TONE: Record<OccurrenceStatus, 'neutral' | 'danger' | 'warning' | 'success'> = {
  PENDING: 'neutral',
  PARTIAL: 'warning',
  PAID: 'success',
  OVERDUE: 'danger',
};

export function StatusBadge({ status }: { status: OccurrenceStatus }) {
  const { t } = useTranslation();
  return <Badge tone={STATUS_TONE[status]}>{t(`expenses.status.${status}`)}</Badge>;
}

/** "Vence hoy" · "Vence en 4 días" · "Venció hace 2 días" (solo si no está pagado). */
export function DueText({ o }: { o: Pick<Occurrence, 'dueDate' | 'daysLeft' | 'status'> }) {
  const { t } = useTranslation();
  const date = formatDate(o.dueDate);
  if (o.status === 'PAID') return <span>{date}</span>;
  const rel =
    o.daysLeft === 0
      ? t('expenses.due.today')
      : o.daysLeft > 0
        ? t('expenses.due.inDays', { count: o.daysLeft })
        : t('expenses.due.agoDays', { count: -o.daysLeft });
  const urgent = o.daysLeft <= 3;
  return (
    <span>
      {date} ·{' '}
      <span className={urgent ? 'font-medium text-red-700 dark:text-red-400' : undefined}>{rel}</span>
    </span>
  );
}

export function Money({ value, currency, className }: { value: number; currency: Currency; className?: string }) {
  return <span className={className ?? 'tabular-nums'}>{formatMoney(value, currency)}</span>;
}

export function InstallmentText({ o }: { o: Pick<Occurrence, 'installmentNumber' | 'installments'> }) {
  const { t } = useTranslation();
  if (!o.installmentNumber || !o.installments) return null;
  return <Badge tone="info">{t('expenses.installmentOf', { n: o.installmentNumber, total: o.installments })}</Badge>;
}
