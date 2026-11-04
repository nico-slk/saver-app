import { INTL_LOCALE, currentLanguage } from '@/i18n';

export type Currency = 'ARS' | 'USD';

/** Formatea montos según el idioma activo: es → $ 1.234,56 · en → $1,234.56 */
export function formatMoney(amount: number, currency: Currency = 'ARS'): string {
  return new Intl.NumberFormat(INTL_LOCALE[currentLanguage()], {
    style: 'currency',
    currency,
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  }).format(amount);
}

/**
 * Las fechas sin hora ("2026-10-25", vencimientos) se interpretan en hora local:
 * `new Date("2026-10-25")` sería UTC y en Argentina mostraría el día 24.
 */
export function parseDate(date: string | Date): Date {
  if (date instanceof Date) return date;
  const m = /^(\d{4})-(\d{2})-(\d{2})$/.exec(date);
  return m ? new Date(Number(m[1]), Number(m[2]) - 1, Number(m[3])) : new Date(date);
}

export function formatDate(date: string | Date, opts: Intl.DateTimeFormatOptions = { dateStyle: 'medium' }): string {
  const d = parseDate(date);
  return new Intl.DateTimeFormat(INTL_LOCALE[currentLanguage()], opts).format(d);
}

export function formatPercent(value: number, digits = 1): string {
  return new Intl.NumberFormat(INTL_LOCALE[currentLanguage()], {
    style: 'percent',
    minimumFractionDigits: digits,
    maximumFractionDigits: digits,
  }).format(value);
}
