import { api } from '@/lib/api';
import type { Currency } from '@/lib/format';
import type { MonthKey } from '@/lib/month';

export type ProjectionEvent = {
  type: 'INSTALLMENTS_END' | 'INCREASE' | 'INCOME_INCREASE' | 'INCOME_ENDS';
  refId: string;
  description: string;
  amount: number | null;
};

export type ProjectionMonth = {
  month: MonthKey;
  income: number;
  expenses: number;
  installments: number;
  recurring: number;
  oneTime: number;
  balance: number;
  cumulative: number;
  cumulativeReal: number;
  inflationPercent: number;
  inflationOfficial: boolean;
  events: ProjectionEvent[];
};

export type ProjectionResponse = {
  from: MonthKey;
  to: MonthKey;
  months: number;
  currency: Currency;
  startBalance: number;
  inflation: {
    monthlyPercent: number;
    source: 'SCREEN' | 'MANUAL' | 'ESTIMATED' | 'NONE';
    estimatedPercent?: number;
    manualPercent?: number;
    lastOfficialMonth?: MonthKey;
    accumulatedPercent: number;
  };
  series: ProjectionMonth[];
  summary: {
    finalBalance: number;
    finalBalanceReal: number;
    averageMonthlySavings: number;
    installmentsEndMonth: MonthKey | null;
    averageSavingsAfterInstallments: number | null;
    negativeMonths: number;
  };
  excludedCurrencies: Currency[];
};

export type ProjectionParams = { months: number; startBalance?: string; inflationPercent?: string };

export const projectionsApi = {
  get: (p: ProjectionParams) =>
    api
      .get<ProjectionResponse>('/api/projections', {
        params: {
          months: p.months,
          startBalance: p.startBalance || undefined,
          inflationPercent: p.inflationPercent === '' || p.inflationPercent == null ? undefined : p.inflationPercent,
        },
      })
      .then((r) => r.data),
};
