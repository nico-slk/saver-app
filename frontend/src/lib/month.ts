import { INTL_LOCALE, currentLanguage } from '@/i18n';

/** Mes en formato "AAAA-MM" (igual que YearMonth en el backend). */
export type MonthKey = string;

export function monthKey(date: Date = new Date()): MonthKey {
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}`;
}

export function addMonths(key: MonthKey, delta: number): MonthKey {
  const [y, m] = key.split('-').map(Number) as [number, number];
  return monthKey(new Date(y, m - 1 + delta, 1));
}

export function isMonthKey(value: string | null | undefined): value is MonthKey {
  return !!value && /^\d{4}-(0[1-9]|1[0-2])$/.test(value);
}

/** "Octubre de 2026" / "October 2026"; short → "Oct 2026". */
export function formatMonth(key: MonthKey, short = false): string {
  const [y, m] = key.split('-').map(Number) as [number, number];
  const text = new Intl.DateTimeFormat(INTL_LOCALE[currentLanguage()], {
    month: short ? 'short' : 'long',
    year: 'numeric',
  }).format(new Date(y, m - 1, 1));
  return text.charAt(0).toUpperCase() + text.slice(1);
}

export function monthsBetween(from: MonthKey, to: MonthKey): number {
  const [fy, fm] = from.split('-').map(Number) as [number, number];
  const [ty, tm] = to.split('-').map(Number) as [number, number];
  return (ty - fy) * 12 + (tm - fm);
}
