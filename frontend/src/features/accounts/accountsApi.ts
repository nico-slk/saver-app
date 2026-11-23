import { api } from '@/lib/api';
import type { Currency } from '@/lib/format';
import type { MonthKey } from '@/lib/month';

export type AccountKind = 'CHECKING' | 'CASH' | 'WALLET' | 'SAVINGS' | 'INVESTMENT';
export const ACCOUNT_KINDS: AccountKind[] = ['CHECKING', 'WALLET', 'CASH', 'SAVINGS', 'INVESTMENT'];

export type Account = {
  id: string;
  name: string;
  kind: AccountKind;
  currency: Currency;
  color: string;
  openingBalance: number;
  openingDate: string;
  balance: number;
  savings: boolean;
  archived: boolean;
};

export type AccountsOverview = {
  accounts: Account[];
  totals: { currency: Currency; balance: number; savings: number }[];
  baseCurrency: Currency;
  /** null si hay cuentas en otra moneda y no hay cotización */
  netWorth?: number;
  netWorthSavings?: number;
  valuationRate?: number;
  valuationType?: string;
};

export type Movement = {
  date: string;
  kind: 'PAYMENT' | 'RECEIPT' | 'TRANSFER_IN' | 'TRANSFER_OUT' | 'ADJUSTMENT';
  description?: string;
  amount: number;
  refId: string;
  month?: MonthKey;
  counted: boolean;
};

export type Card = { id: string; name: string; color: string; archived: boolean };

export type RateType = 'OFICIAL' | 'MEP' | 'BLUE' | 'TARJETA' | 'CCL' | 'MAYORISTA' | 'CRIPTO';
export type ExchangeRate = { type: RateType; day: string; buy?: number; sell: number };
export type Purpose = 'PAYMENT' | 'PAYMENT_CARD' | 'RECEIPT' | 'TRANSFER' | 'VALUATION';
export type Conversion = { amount: number; rate?: number; type?: string };

export type AccountInput = {
  name: string;
  kind: AccountKind;
  currency: Currency;
  openingBalance?: string;
  openingDate?: string;
  color?: string;
};

export const accountKeys = { all: ['accounts'] as const, cards: ['cards'] as const, rates: ['exchange-rates'] as const };

export const accountsApi = {
  overview: () => api.get<AccountsOverview>('/api/accounts').then((r) => r.data),
  create: (input: AccountInput) => api.post<Account>('/api/accounts', input).then((r) => r.data),
  update: (id: string, input: Partial<AccountInput> & { archived?: boolean }) =>
    api.patch<Account>(`/api/accounts/${id}`, input).then((r) => r.data),
  remove: (id: string) => api.delete<void>(`/api/accounts/${id}`),
  movements: (id: string) => api.get<Movement[]>(`/api/accounts/${id}/movements`).then((r) => r.data),
  transfer: (input: { fromAccountId: string; toAccountId: string; amount: string; toAmount?: string; rate?: string; date?: string; notes?: string }) =>
    api.post('/api/transfers', input).then((r) => r.data),
  deleteTransfer: (id: string) => api.delete<void>(`/api/transfers/${id}`),
  adjust: (id: string, input: { targetBalance: string; date?: string; notes?: string }) =>
    api.post(`/api/accounts/${id}/adjustments`, input).then((r) => r.data),
  deleteAdjustment: (id: string) => api.delete<void>(`/api/adjustments/${id}`),
  cards: () => api.get<Card[]>('/api/cards').then((r) => r.data),
  createCard: (input: { name: string; color?: string }) => api.post<Card>('/api/cards', input).then((r) => r.data),
  archiveCard: (id: string) => api.delete<void>(`/api/cards/${id}`),
  rates: () => api.get<ExchangeRate[]>('/api/exchange-rates').then((r) => r.data),
  convert: (p: { amount: string; from: Currency; to: Currency; purpose: Purpose; rate?: string }) =>
    api.get<Conversion>('/api/exchange-rates/convert', { params: { ...p, rate: p.rate || undefined } }).then((r) => r.data),
};
