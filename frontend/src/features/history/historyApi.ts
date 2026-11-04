import { api } from '@/lib/api';
import type { Currency } from '@/lib/format';
import type { MonthKey } from '@/lib/month';
import type { ExpenseKind } from '@/features/expenses/expensesApi';

export type Change = { from: number; to: number; diff: number; percent: number | null };

export type HistoryMonth = {
  month: MonthKey;
  income: number;
  /** Con el filtro de categorías aplicado */
  expenses: number;
  /** Todos los gastos (sin filtro) */
  expensesTotal: number;
  savings: number;
  byCategory: { categoryId: string; amount: number }[];
  /** Inflación del mes en % (oficial o estimada). */
  inflationPercent: number;
  inflationOfficial: boolean;
  /** Multiplicador para expresar el mes en moneda del último mes del período. */
  realFactor: number;
};

export type HistoryResponse = {
  from: MonthKey;
  to: MonthKey;
  currency: Currency;
  categoryFilter: string[];
  months: HistoryMonth[];
  summary: {
    savingsVsPreviousMonth: Change;
    averageSavings: number;
    totalIncome: number;
    totalExpenses: number;
    totalSavings: number;
    expensesFirstToLast: Change;
    inflationPercent: number;
    inflationEstimated: boolean;
    totalSavingsReal: number;
    expensesRealPercent?: number;
  };
};

export type ExpenseChange = {
  expenseId: string;
  description: string;
  categoryId: string;
  kind: ExpenseKind;
  from: number | null;
  to: number | null;
  diff: number;
  percent: number | null;
  status: 'UP' | 'DOWN' | 'SAME' | 'NEW' | 'ENDED';
  /** Variación descontada la inflación (positivo = subió más que la inflación). */
  realPercent?: number;
};

export type CompareResponse = {
  from: MonthKey;
  to: MonthKey;
  currency: Currency;
  income: Change;
  expenses: Change;
  savings: Change;
  averageSavings: number;
  months: number;
  items: ExpenseChange[];
  categories: { categoryId: string; change: Change }[];
  inflationPercent: number;
  inflationEstimated: boolean;
  savingsReal: Change;
  expensesRealPercent?: number;
};

export const historyApi = {
  get: (params: { from: MonthKey; to?: MonthKey; categories?: string[] }) =>
    api
      .get<HistoryResponse>('/api/history', {
        params: { from: params.from, to: params.to, categories: params.categories?.join(',') || undefined },
      })
      .then((r) => r.data),
  compare: (from: MonthKey, to?: MonthKey) =>
    api.get<CompareResponse>('/api/history/compare', { params: { from, to } }).then((r) => r.data),
};
