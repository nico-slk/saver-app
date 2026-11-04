import { Link } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import { Alert, Card } from '@/components/ui';
import { EmptyState, InlineLoader } from '@/components/ui/overlay';
import { translateError } from '@/lib/errors';
import type { Currency } from '@/lib/format';
import { formatMonth } from '@/lib/month';
import { useAuthStore } from '@/stores/authStore';
import { useCategories } from '@/features/categories/useCategories';
import { OccurrenceList } from '@/features/expenses/OccurrenceList';
import { CategoryBars } from '@/features/months/charts';
import { MonthSummary } from '@/features/months/MonthSummary';
import { monthsApi, queryKeys } from '@/features/months/monthsApi';
import { TimelineCard } from '@/features/timeline/TimelineCard';

export function DashboardPage() {
  const { t } = useTranslation();
  const user = useAuthStore((s) => s.user);
  const { nameOf } = useCategories();
  const current = useQuery({ queryKey: queryKeys.month('current'), queryFn: () => monthsApi.get('current') });
  const view = current.data;
  const currency: Currency = view?.totals.find((c) => c.currency === user?.baseCurrency)?.currency ?? view?.totals[0]?.currency ?? 'ARS';

  const upcoming = view
    ? [...view.overdue, ...view.occurrences.filter((o) => o.status !== 'PAID')].sort((a, b) => a.dueDate.localeCompare(b.dueDate))
    : [];
  const byCategory = (view?.byCategory ?? [])
    .filter((c) => c.currency === currency)
    .map((c) => ({ name: nameOf(c.categoryId), amount: c.amount }));

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-bold">{t('dashboard.greeting', { name: user?.displayName ?? '' })}</h1>
        {view && <p className="text-sm text-slate-500">{formatMonth(view.currentMonth)}</p>}
      </div>
      {current.isError && <Alert>{translateError(t, current.error)}</Alert>}
      {current.isPending && <InlineLoader label={t('common.loading')} />}
      {view && (
        <>
          <MonthSummary totals={view.totals} projected={false} />
          {view.totals.length === 0 && (
            <EmptyState
              title={t('dashboard.empty')}
              action={
                <div className="flex justify-center gap-3 text-sm">
                  <Link to="/incomes" className="font-medium text-brand-700 hover:underline dark:text-brand-500">{t('dashboard.addIncome')}</Link>
                  <Link to="/expenses?tab=templates" className="font-medium text-brand-700 hover:underline dark:text-brand-500">{t('dashboard.addExpense')}</Link>
                </div>
              }
            />
          )}
          <div className="grid gap-6 lg:grid-cols-2">
            <Card>
              <div className="mb-1 flex items-center justify-between">
                <h2 className="font-semibold">{t('dashboard.upcoming')}</h2>
                <Link to="/expenses" className="text-sm text-brand-700 hover:underline dark:text-brand-500">{t('dashboard.seeAll')}</Link>
              </div>
              {upcoming.length === 0 ? (
                <p className="py-6 text-center text-sm text-slate-500">{t('dashboard.noUpcoming')}</p>
              ) : (
                <OccurrenceList items={upcoming.slice(0, 6)} compact />
              )}
            </Card>
            <Card>
              <h2 className="mb-3 font-semibold">{t('dashboard.byCategory')}</h2>
              {byCategory.length === 0 ? (
                <p className="py-6 text-center text-sm text-slate-500">{t('dashboard.noExpenses')}</p>
              ) : (
                <CategoryBars data={byCategory} currency={currency} />
              )}
            </Card>
          </div>
          {view.totals.length > 0 && <TimelineCard currency={currency} />}
        </>
      )}
    </div>
  );
}
