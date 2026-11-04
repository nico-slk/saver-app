import { api } from '@/lib/api';
import type { Currency } from '@/lib/format';
import type { MonthKey } from '@/lib/month';

/** De dónde sale la inflación de los meses sin dato oficial. */
export type ProjectedSource = 'MANUAL' | 'ESTIMATED' | 'NONE';

/** Porcentajes mensuales (1.9 = 1,9 %). */
export type CurrencyInflation = {
  currency: Currency;
  source: string;
  lastOfficialMonth?: MonthKey;
  lastOfficialPercent?: number;
  last12MonthsPercent?: number;
  estimateMonths: number;
  estimatedPercent?: number;
  manualPercent?: number;
  projectedPercent: number;
  projectedSource: ProjectedSource;
  lastSync?: string;
  recent: { month: MonthKey; percent: number }[];
};

export type InflationOverview = { currencies: CurrencyInflation[] };

export const inflationKeys = { all: ['inflation'] as const };

export const inflationApi = {
  overview: () => api.get<InflationOverview>('/api/inflation').then((r) => r.data),
  setManual: (currency: Currency, monthlyPercent: string) =>
    api.put<InflationOverview>(`/api/inflation/${currency}/manual`, { monthlyPercent }).then((r) => r.data),
  clearManual: (currency: Currency) => api.delete<void>(`/api/inflation/${currency}/manual`),
};
