import { describe, expect, it } from 'vitest';
import { parseDate } from './format';

describe('parseDate', () => {
  it('treats YYYY-MM-DD as a local date (no UTC day shift)', () => {
    const d = parseDate('2026-10-25');
    expect([d.getFullYear(), d.getMonth(), d.getDate()]).toEqual([2026, 9, 25]);
  });
  it('keeps full ISO timestamps as-is', () => {
    expect(parseDate('2026-10-21T20:00:00Z').toISOString()).toBe('2026-10-21T20:00:00.000Z');
  });
});
