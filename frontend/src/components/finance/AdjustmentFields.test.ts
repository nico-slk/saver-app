import { describe, expect, it } from 'vitest';
import { adjustmentPayload, adjustmentState } from './AdjustmentFields';

describe('adjustment rule', () => {
  it('is empty when disabled', () => {
    expect(adjustmentPayload(adjustmentState())).toEqual({ adjustEveryMonths: null, adjustPercent: null, adjustWithInflation: false });
  });

  it('sends inflation without a percentage', () => {
    const s = { ...adjustmentState(), enabled: true, everyMonths: '4', mode: 'inflation' as const, percent: '12' };
    expect(adjustmentPayload(s)).toEqual({ adjustEveryMonths: 4, adjustPercent: null, adjustWithInflation: true });
  });

  it('reads an existing rule', () => {
    const s = adjustmentState({ adjustEveryMonths: 3, adjustPercent: 10, adjustWithInflation: false });
    expect(s).toMatchObject({ enabled: true, everyMonths: '3', mode: 'percent', percent: '10' });
    expect(adjustmentPayload(s)).toEqual({ adjustEveryMonths: 3, adjustPercent: '10', adjustWithInflation: false });
  });
});
