import { api } from '@/lib/api';
import type { Currency } from '@/lib/format';
import type { MonthKey } from '@/lib/month';

export type TimelinePoint = {
  month: MonthKey;
  /** PAST = real, CURRENT = mes en curso, FUTURE = proyectado. */
  type: 'PAST' | 'CURRENT' | 'FUTURE';
  income: number;
  expenses: number;
  balance: number;
};

export type TimelineResponse = {
  from: MonthKey;
  to: MonthKey;
  current: MonthKey;
  today: string;
  currency: Currency;
  points: TimelinePoint[];
};

export const timelineApi = {
  get: (p: { from?: MonthKey; months?: number; currency: Currency }) =>
    api.get<TimelineResponse>('/api/timeline', { params: p }).then((r) => r.data),
};
