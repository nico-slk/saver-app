import { isAxiosError } from 'axios';
import type { TFunction } from 'i18next';

type Problem = {
  code?: string;
  fields?: Record<string, string>;
  properties?: { code?: string; fields?: Record<string, string> };
};

/** Extrae el código de error estable que devuelve el backend (ProblemDetail.code). */
export function errorCode(err: unknown): string {
  if (isAxiosError<Problem>(err)) {
    if (!err.response) return 'network';
    const data = err.response.data;
    return data?.code ?? data?.properties?.code ?? 'unknown';
  }
  return 'unknown';
}

export function fieldErrors(err: unknown): Record<string, string> {
  if (isAxiosError<Problem>(err)) {
    const data = err.response?.data;
    return data?.fields ?? data?.properties?.fields ?? {};
  }
  return {};
}

/** Traduce un código ("auth.invalid_credentials") a texto en el idioma actual. */
export function translateError(t: TFunction, codeOrErr: unknown): string {
  const code = typeof codeOrErr === 'string' ? codeOrErr : errorCode(codeOrErr);
  return t(`errors.${code}`, { defaultValue: t('errors.unknown') });
}
