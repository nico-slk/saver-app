import axios, { AxiosError, type InternalAxiosRequestConfig } from 'axios';
import i18n from '@/i18n';

/**
 * Cliente HTTP. El access token vive solo en memoria (zustand); el refresh token
 * en una cookie HttpOnly que el navegador envía a /api/auth/*.
 */
export const api = axios.create({
  baseURL: import.meta.env.VITE_API_URL || '',
  withCredentials: true,
  headers: { 'Content-Type': 'application/json' },
});

type TokenAccessors = {
  getToken: () => string | null;
  refresh: () => Promise<string | null>;
  onSessionExpired: () => void;
};

let accessors: TokenAccessors | null = null;

/** Lo llama el authStore al inicializarse (evita dependencia circular). */
export function bindAuth(a: TokenAccessors) {
  accessors = a;
}

api.interceptors.request.use((config) => {
  const token = accessors?.getToken();
  if (token && !config.url?.startsWith('/api/auth/')) config.headers.set('Authorization', `Bearer ${token}`);
  config.headers.set('Accept-Language', i18n.language);
  return config;
});

let refreshing: Promise<string | null> | null = null;

api.interceptors.response.use(
  (res) => res,
  async (error: AxiosError) => {
    const original = error.config as (InternalAxiosRequestConfig & { _retried?: boolean }) | undefined;
    const isAuthCall = original?.url?.startsWith('/api/auth/');
    if (error.response?.status === 401 && original && !original._retried && !isAuthCall && accessors) {
      original._retried = true;
      // Un único refresh compartido para todas las requests que fallaron en paralelo.
      refreshing ??= accessors.refresh().finally(() => {
        refreshing = null;
      });
      const token = await refreshing;
      if (token) {
        original.headers.set('Authorization', `Bearer ${token}`);
        return api(original);
      }
      accessors.onSessionExpired();
    }
    return Promise.reject(error);
  },
);
