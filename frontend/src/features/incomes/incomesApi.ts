import { api } from '@/lib/api';
import type { Currency } from '@/lib/format';
import type { MonthKey } from '@/lib/month';

export type IncomeKind = 'FIXED' | 'EXTRA';
export type AmountVersion = { effectiveMonth: MonthKey; amount: number };

export type Income = {
  id: string;
  description: string;
  kind: IncomeKind;
  currency: Currency;
  startMonth: MonthKey;
  endMonth: MonthKey | null;
  recurring: boolean;
  currentAmount: number | null;
  amounts: AmountVersion[];
  adjustEveryMonths: number | null;
  adjustPercent: number | null;
  adjustWithInflation: boolean;
  nextAdjustmentMonth: MonthKey | null;
};

type AdjustmentInput = {
  adjustEveryMonths?: number | null;
  adjustPercent?: string | null;
  adjustWithInflation?: boolean;
};

/** months: 1 = puntual, N = se repite N meses, null = indefinido. */
export type IncomeInput = {
  description: string;
  kind: IncomeKind;
  currency: Currency;
  amount: string;
  startMonth: MonthKey;
  months: number | null;
} & AdjustmentInput;

export const incomesApi = {
  list: () => api.get<Income[]>('/api/incomes').then((r) => r.data),
  create: (input: IncomeInput) => api.post<Income>('/api/incomes', input).then((r) => r.data),
  update: (id: string, input: { description?: string; endMonth?: MonthKey; clearEnd?: boolean; clearAdjustment?: boolean } & AdjustmentInput) =>
    api.patch<Income>(`/api/incomes/${id}`, input).then((r) => r.data),
  changeAmount: (id: string, input: { effectiveMonth: MonthKey; amount: string }) =>
    api.post<Income>(`/api/incomes/${id}/amounts`, input).then((r) => r.data),
  remove: (id: string) => api.delete<void>(`/api/incomes/${id}`),
};
