import { api } from '@/lib/api';
import type { Currency } from '@/lib/format';
import type { MonthKey } from '@/lib/month';
import type { AmountVersion } from '@/features/incomes/incomesApi';

export type ExpenseKind = 'ONE_TIME' | 'INSTALLMENTS' | 'RECURRING';

export type Expense = {
  id: string;
  description: string;
  categoryId: string;
  kind: ExpenseKind;
  currency: Currency;
  startMonth: MonthKey;
  endMonth: MonthKey | null;
  installments: number | null;
  remainingInstallments: number | null;
  dueDay: number | null;
  variableAmount: boolean;
  adjustEveryMonths: number | null;
  adjustPercent: number | null;
  adjustWithInflation: boolean;
  nextAdjustmentMonth: MonthKey | null;
  currentAmount: number;
  totalAmount: number | null;
  amounts: AmountVersion[];
  notes: string | null;
};

export type ExpenseInput = {
  description: string;
  categoryId: string;
  kind: ExpenseKind;
  currency: Currency;
  amount: string;
  startMonth: MonthKey;
  installments?: number | null;
  dueDay?: number | null;
  variableAmount: boolean;
  adjustEveryMonths?: number | null;
  adjustPercent?: string | null;
  adjustWithInflation: boolean;
  notes?: string | null;
};

export type ExpenseUpdate = {
  description?: string;
  categoryId?: string;
  /** 0 = último día del mes */
  dueDay?: number;
  variableAmount?: boolean;
  adjustEveryMonths?: number;
  adjustPercent?: string;
  adjustWithInflation?: boolean;
  clearAdjustment?: boolean;
  endMonth?: MonthKey;
  clearEnd?: boolean;
  notes?: string;
};

export type OccurrenceStatus = 'PENDING' | 'PARTIAL' | 'PAID' | 'OVERDUE';

export type Occurrence = {
  id: string | null;
  expenseId: string;
  description: string;
  categoryId: string;
  kind: ExpenseKind;
  month: MonthKey;
  dueDate: string;
  daysLeft: number;
  installmentNumber: number | null;
  installments: number | null;
  expectedAmount: number;
  paidAmount: number;
  currency: Currency;
  status: OccurrenceStatus;
  variableAmount: boolean;
  projected: boolean;
  notes: string | null;
};

export const expensesApi = {
  list: () => api.get<Expense[]>('/api/expenses').then((r) => r.data),
  create: (input: ExpenseInput) => api.post<Expense>('/api/expenses', input).then((r) => r.data),
  update: (id: string, input: ExpenseUpdate) => api.patch<Expense>(`/api/expenses/${id}`, input).then((r) => r.data),
  changeAmount: (id: string, input: { effectiveMonth: MonthKey; amount: string }) =>
    api.post<Expense>(`/api/expenses/${id}/amounts`, input).then((r) => r.data),
  remove: (id: string) => api.delete<void>(`/api/expenses/${id}`),
  updateOccurrence: (id: string, input: { dueDate?: string; expectedAmount?: string; notes?: string }) =>
    api.patch<Occurrence>(`/api/occurrences/${id}`, input).then((r) => r.data),
};
