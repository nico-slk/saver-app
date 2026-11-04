import { describe, expect, it } from 'vitest';
import { AxiosError, AxiosHeaders } from 'axios';
import { errorCode, fieldErrors } from './errors';

function axiosErr(status: number | null, data?: unknown) {
  const err = new AxiosError('x');
  if (status !== null) {
    err.response = { status, data, statusText: '', headers: {}, config: { headers: new AxiosHeaders() } };
  }
  return err;
}

describe('errorCode', () => {
  it('reads ProblemDetail.code', () => {
    expect(errorCode(axiosErr(401, { code: 'auth.invalid_credentials' }))).toBe('auth.invalid_credentials');
  });
  it('reads nested properties.code', () => {
    expect(errorCode(axiosErr(400, { properties: { code: 'validation.failed' } }))).toBe('validation.failed');
  });
  it('detects network errors', () => {
    expect(errorCode(axiosErr(null))).toBe('network');
  });
  it('falls back to unknown', () => {
    expect(errorCode(new Error('boom'))).toBe('unknown');
  });
  it('extracts field errors', () => {
    expect(fieldErrors(axiosErr(400, { fields: { email: 'validation.email' } }))).toEqual({ email: 'validation.email' });
  });
});
