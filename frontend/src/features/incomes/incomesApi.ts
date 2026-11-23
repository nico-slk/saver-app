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

export type Receipt = {
  id: string;
  incomeId: string;
  month: MonthKey;
  accountId: string;
  accountName?: string;
  accountCurrency?: Currency;
  receivedOn: string;
  amount: number;
  currency: Currency;
  accountAmount: number;
  rate?: number;
  rateType?: string;
  notes?: string;
};

export const incomesApi = {
  receipts: (id: string, month: MonthKey) =>
    api.get<Receipt[]>(`/api/incomes/${id}/receipts`, { params: { month } }).then((r) => r.data),
  receive: (id: string, input: { month: MonthKey; accountId: string; amount: string; date?: string; rate?: string; notes?: string }) =>
    api.post<Receipt[]>(`/api/incomes/${id}/receipts`, { ...input, rate: input.rate || undefined }).then((r) => r.data),
  removeReceipt: (id: string) => api.delete<void>(`/api/incomes/receipts/${id}`),
  list: () => api.get<Income[]>('/api/incomes').then((r) => r.data),
  create: (input: IncomeInput) => api.post<Income>('/api/incomes', input).then((r) => r.data),
  update: (id: string, input: { description?: string; endMonth?: MonthKey; clearEnd?: boolean; clearAdjustment?: boolean } & AdjustmentInput) =>
    api.patch<Income>(`/api/incomes/${id}`, input).then((r) => r.data),
  changeAmount: (id: string, input: { effectiveMonth: MonthKey; amount: string }) =>
    api.post<Income>(`/api/incomes/${id}/amounts`, input).then((r) => r.data),
  remove: (id: string) => api.delete<void>(`/api/incomes/${id}`),
};
