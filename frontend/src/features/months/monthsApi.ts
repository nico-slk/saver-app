import { api } from '@/lib/api';
import type { Currency } from '@/lib/format';
import type { MonthKey } from '@/lib/month';
import type { Occurrence } from '@/features/expenses/expensesApi';
import type { IncomeKind } from '@/features/incomes/incomesApi';

export type CurrencyTotals = {
  currency: Currency;
  income: number;
  expenses: number;
  paid: number;
  pending: number;
  overdue: number;
  balance: number;
};

export type MonthView = {
  month: MonthKey;
  currentMonth: MonthKey;
  projected: boolean;
  totals: CurrencyTotals[];
  byCategory: { categoryId: string; currency: Currency; amount: number }[];
  incomes: { incomeId: string; description: string; kind: IncomeKind; currency: Currency; amount: number; recurring: boolean }[];
  occurrences: Occurrence[];
  overdue: Occurrence[];
};

export const monthsApi = {
  get: (month: MonthKey | 'current') => api.get<MonthView>(`/api/months/${month}`).then((r) => r.data),
};

/** Claves de TanStack Query compartidas: al modificar gastos o ingresos se invalidan todas. */
export const queryKeys = {
  month: (m: string) => ['months', m] as const,
  expenses: ['expenses'] as const,
  incomes: ['incomes'] as const,
};
