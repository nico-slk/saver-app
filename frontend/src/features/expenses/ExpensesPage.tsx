import { useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import { useMutation, useQuery } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import { Alert, Button, Card } from '@/components/ui';
import { Badge, ConfirmDialog, EmptyState, InlineLoader, Tabs } from '@/components/ui/overlay';
import { AmountChangeDialog, EndDialog, MonthNavigator } from '@/components/finance/AmountChangeDialog';
import { translateError } from '@/lib/errors';
import { formatMoney } from '@/lib/format';
import { formatMonth, isMonthKey, monthKey, type MonthKey } from '@/lib/month';
import { CategoriesPanel } from '@/features/categories/CategoriesPanel';
import { MonthSummary } from '@/features/months/MonthSummary';
import { monthsApi, queryKeys } from '@/features/months/monthsApi';
import { useFinanceInvalidation } from '@/features/months/useFinanceInvalidation';
import { expensesApi, type Expense } from './expensesApi';
import { ExpenseForm } from './ExpenseForm';
import { OccurrenceList } from './OccurrenceList';
import { CategoryChip } from './parts';

type Tab = 'month' | 'templates' | 'categories';

export function ExpensesPage() {
  const { t } = useTranslation();
  const [params, setParams] = useSearchParams();
  const tab = (params.get('tab') as Tab | null) ?? 'month';
  const month = isMonthKey(params.get('month')) ? params.get('month')! : monthKey();
  const [creating, setCreating] = useState(false);

  const update = (next: Partial<{ tab: Tab; month: MonthKey }>) => {
    const p = new URLSearchParams(params);
    if (next.tab) p.set('tab', next.tab);
    if (next.month) p.set('month', next.month);
    setParams(p, { replace: true });
  };

  return (
    <div className="space-y-5">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <h1 className="text-2xl font-bold">{t('nav.expenses')}</h1>
        <Button onClick={() => setCreating(true)}>+ {t('expenses.new')}</Button>
      </div>
      <Tabs<Tab>
        value={tab}
        onChange={(v) => update({ tab: v })}
        items={[
          { value: 'month', label: t('expenses.tabs.month') },
          { value: 'templates', label: t('expenses.tabs.templates') },
          { value: 'categories', label: t('expenses.tabs.categories') },
        ]}
      />
      {tab === 'month' && <MonthTab month={month} onMonth={(m) => update({ month: m })} />}
      {tab === 'templates' && <TemplatesTab onCreate={() => setCreating(true)} />}
      {tab === 'categories' && <CategoriesPanel />}
      {creating && <ExpenseForm onClose={() => setCreating(false)} />}
    </div>
  );
}

function MonthTab({ month, onMonth }: { month: MonthKey; onMonth: (m: MonthKey) => void }) {
  const { t } = useTranslation();
  const q = useQuery({ queryKey: queryKeys.month(month), queryFn: () => monthsApi.get(month) });
  const view = q.data;

  return (
    <div className="space-y-5">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <MonthNavigator value={month} onChange={onMonth} label={formatMonth(month)} />
        {view?.projected && <Badge tone="info">{t('month.projected')}</Badge>}
      </div>
      {q.isError && <Alert>{translateError(t, q.error)}</Alert>}
      {q.isPending && <InlineLoader label={t('common.loading')} />}
      {view && (
        <>
          <MonthSummary totals={view.totals} projected={view.projected} showOverdue={view.month === view.currentMonth} />
          {view.overdue.length > 0 && (
            <Card className="border-red-200 dark:border-red-900">
              <h2 className="mb-1 font-semibold text-red-700 dark:text-red-400">{t('expenses.overdueTitle', { count: view.overdue.length })}</h2>
              <OccurrenceList items={view.overdue} />
            </Card>
          )}
          <Card>
            <h2 className="mb-1 font-semibold">{t('expenses.monthList')}</h2>
            {view.occurrences.length === 0 ? (
              <EmptyState title={t('expenses.emptyMonth')} />
            ) : (
              <OccurrenceList items={view.occurrences} groupByCard />
            )}
          </Card>
        </>
      )}
    </div>
  );
}

function TemplatesTab({ onCreate }: { onCreate: () => void }) {
  const { t } = useTranslation();
  const q = useQuery({ queryKey: queryKeys.expenses, queryFn: expensesApi.list });
  const refresh = useFinanceInvalidation();
  const [editing, setEditing] = useState<Expense | null>(null);
  const [raising, setRaising] = useState<Expense | null>(null);
  const [ending, setEnding] = useState<Expense | null>(null);
  const [deleting, setDeleting] = useState<Expense | null>(null);
  const remove = useMutation({
    mutationFn: (id: string) => expensesApi.remove(id),
    onSuccess: async () => {
      setDeleting(null);
      await refresh();
    },
  });

  if (q.isPending) return <InlineLoader label={t('common.loading')} />;
  const list = q.data ?? [];
  const now = monthKey();
  const active = list.filter((e) => !e.endMonth || e.endMonth >= now);
  const finished = list.filter((e) => e.endMonth && e.endMonth < now);

  if (list.length === 0) {
    return <EmptyState title={t('expenses.emptyTemplates')} action={<Button onClick={onCreate}>+ {t('expenses.new')}</Button>} />;
  }

  const row = (e: Expense) => (
    <li key={e.id} className="flex flex-col gap-2 py-3 sm:flex-row sm:items-center sm:gap-4">
      <div className="min-w-0 flex-1">
        <div className="flex flex-wrap items-center gap-2">
          <span className="font-medium">{e.description}</span>
          <Badge>{t(`expenses.kind.${e.kind}`)}</Badge>
          {e.variableAmount && <Badge tone="warning">{t('expenses.variable')}</Badge>}
        </div>
        <div className="mt-1 flex flex-wrap gap-x-3 gap-y-1 text-xs text-slate-500">
          <CategoryChip id={e.categoryId} />
          <span>{e.dueDay ? t('expenses.dueEvery', { day: e.dueDay }) : t('expenses.dueLastDay')}</span>
          {e.kind === 'INSTALLMENTS' && (
            <span>{t('expenses.installmentsSummary', { remaining: e.remainingInstallments, total: e.installments, end: formatMonth(e.endMonth!, true) })}</span>
          )}
          {e.kind === 'RECURRING' && (
            <span>{e.endMonth ? t('expenses.until', { month: formatMonth(e.endMonth, true) }) : t('expenses.indefinite')}</span>
          )}
          {e.nextAdjustmentMonth && (
            <span>{e.adjustWithInflation
              ? t('expenses.nextAdjustmentInflation', { month: formatMonth(e.nextAdjustmentMonth, true) })
              : t('expenses.nextAdjustment', { month: formatMonth(e.nextAdjustmentMonth, true), pct: e.adjustPercent ?? 0 })}</span>
          )}
        </div>
      </div>
      <div className="flex items-center justify-between gap-2 sm:justify-end">
        <div className="text-right">
          <div className="font-semibold tabular-nums">{formatMoney(e.currentAmount, e.currency)}</div>
          {e.totalAmount != null && <div className="text-xs text-slate-500">{t('expenses.total', { amount: formatMoney(e.totalAmount, e.currency) })}</div>}
        </div>
        <div className="flex flex-wrap justify-end gap-1">
          <Button variant="ghost" className="px-2 py-1 text-xs" onClick={() => setEditing(e)}>{t('common.edit')}</Button>
          {e.kind !== 'ONE_TIME' && (
            <Button variant="ghost" className="px-2 py-1 text-xs" onClick={() => setRaising(e)}>{t('expenses.changeAmount')}</Button>
          )}
          {e.kind === 'RECURRING' && !e.endMonth && (
            <Button variant="ghost" className="px-2 py-1 text-xs" onClick={() => setEnding(e)}>{t('expenses.end')}</Button>
          )}
          <Button variant="ghost" className="px-2 py-1 text-xs text-red-600" onClick={() => setDeleting(e)}>{t('common.delete')}</Button>
        </div>
      </div>
    </li>
  );

  return (
    <div className="space-y-5">
      {remove.isError && <Alert>{translateError(t, remove.error)}</Alert>}
      <Card>
        <h2 className="mb-1 font-semibold">{t('expenses.activeTemplates')}</h2>
        <ul className="divide-y divide-slate-100 dark:divide-slate-800">{active.map(row)}</ul>
      </Card>
      {finished.length > 0 && (
        <Card>
          <h2 className="mb-1 font-semibold text-slate-500">{t('expenses.finishedTemplates')}</h2>
          <ul className="divide-y divide-slate-100 opacity-80 dark:divide-slate-800">{finished.map(row)}</ul>
        </Card>
      )}
      {editing && <ExpenseForm expense={editing} onClose={() => setEditing(null)} />}
      {raising && (
        <AmountChangeDialog
          title={t('expenses.changeAmountTitle', { name: raising.description })}
          hint={t('expenses.changeAmountHint')}
          currentAmount={raising.currentAmount}
          minMonth={raising.startMonth}
          onSubmit={(input) => expensesApi.changeAmount(raising.id, input)}
          onClose={() => setRaising(null)}
        />
      )}
      {ending && (
        <EndDialog
          title={t('expenses.endTitle', { name: ending.description })}
          hint={t('expenses.endHint')}
          minMonth={ending.startMonth}
          onSubmit={(endMonth) => expensesApi.update(ending.id, { endMonth })}
          onClose={() => setEnding(null)}
        />
      )}
      <ConfirmDialog
        open={!!deleting}
        title={t('expenses.deleteTitle')}
        message={t('expenses.deleteMessage', { name: deleting?.description ?? '' })}
        confirmLabel={t('common.delete')}
        loading={remove.isPending}
        onConfirm={() => deleting && remove.mutate(deleting.id)}
        onClose={() => setDeleting(null)}
      />
    </div>
  );
}
