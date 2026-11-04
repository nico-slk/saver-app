import { api } from '@/lib/api';
import type { Currency } from '@/lib/format';
import type { Language } from '@/i18n';

export type User = {
  id: string;
  email: string;
  displayName: string;
  locale: Language;
  baseCurrency: Currency;
  timezone: string;
  hasPassword: boolean;
  googleLinked: boolean;
};

export type AuthResponse = { accessToken: string; expiresIn: number; user: User };
export type Providers = { password: boolean; googleClientId?: string | null };

export type RegisterInput = { email: string; password: string; displayName: string; locale: Language };
export type LoginInput = { email: string; password: string };
export type UpdateUserInput = Partial<Pick<User, 'displayName' | 'locale' | 'baseCurrency' | 'timezone'>>;

export const authApi = {
  providers: () => api.get<Providers>('/api/auth/providers').then((r) => r.data),
  register: (input: RegisterInput) => api.post<AuthResponse>('/api/auth/register', input).then((r) => r.data),
  login: (input: LoginInput) => api.post<AuthResponse>('/api/auth/login', input).then((r) => r.data),
  google: (idToken: string, locale: Language) =>
    api.post<AuthResponse>('/api/auth/google', { idToken, locale }).then((r) => r.data),
  refresh: () => api.post<AuthResponse>('/api/auth/refresh').then((r) => r.data),
  logout: () => api.post<void>('/api/auth/logout'),
  forgotPassword: (email: string) => api.post<void>('/api/auth/password/forgot', { email }),
  resetPassword: (token: string, password: string) => api.post<void>('/api/auth/password/reset', { token, password }),
  me: () => api.get<User>('/api/me').then((r) => r.data),
  updateMe: (input: UpdateUserInput) => api.patch<User>('/api/me', input).then((r) => r.data),
};
