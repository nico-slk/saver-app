import { describe, expect, it } from 'vitest';
import { addMonths, isMonthKey, monthKey, monthsBetween } from './month';

describe('month helpers', () => {
  it('formats a date as YYYY-MM', () => {
    expect(monthKey(new Date(2026, 0, 31))).toBe('2026-01');
  });
  it('adds months across years', () => {
    expect(addMonths('2026-11', 3)).toBe('2027-02');
    expect(addMonths('2026-01', -1)).toBe('2025-12');
  });
  it('validates keys', () => {
    expect(isMonthKey('2026-10')).toBe(true);
    expect(isMonthKey('2026-13')).toBe(false);
    expect(isMonthKey(null)).toBe(false);
  });
  it('counts months between keys', () => {
    expect(monthsBetween('2026-10', '2027-01')).toBe(3);
  });
});
