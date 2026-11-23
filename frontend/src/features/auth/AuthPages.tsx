import { useState, type FormEvent, type ReactNode } from 'react';
import { Link, Navigate, useLocation, useNavigate } from 'react-router-dom';
import { useMutation, useQuery } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import { Alert, Button, Card, Input } from '@/components/ui';
import { PasswordInput } from '@/components/ui/PasswordInput';
import { LanguageSwitcher } from '@/components/layout/LanguageSwitcher';
import { currentLanguage } from '@/i18n';
import { fieldErrors, translateError } from '@/lib/errors';
import { useAuthStore } from '@/stores/authStore';
import { authApi, type AuthResponse } from './authApi';
import { GoogleButton } from './GoogleButton';

export function AuthShell({ title, children }: { title: string; children: ReactNode }) {
  const { t } = useTranslation();
  return (
    <div className="flex min-h-dvh flex-col items-center justify-center px-4 py-10">
      <div className="absolute top-4 right-4">
        <LanguageSwitcher compact />
      </div>
      <div className="mb-6 text-center">
        <img src="/favicon.svg" alt="" className="mx-auto mb-3 size-12" />
        <h1 className="text-2xl font-bold">{t('app.name')}</h1>
        <p className="text-sm text-slate-500">{t('app.tagline')}</p>
      </div>
      <Card className="w-full max-w-sm">
        <h2 className="mb-4 text-lg font-semibold">{title}</h2>
        {children}
      </Card>
    </div>
  );
}

/** Lógica común: tras autenticar, guarda la sesión y vuelve a la página pedida. */
function useAuthSuccess() {
  const setSession = useAuthStore((s) => s.setSession);
  const navigate = useNavigate();
  const location = useLocation();
  const from = (location.state as { from?: string } | null)?.from ?? '/';
  return (res: AuthResponse) => {
    setSession(res);
    navigate(from, { replace: true });
  };
}

function GoogleSection() {
  const { t } = useTranslation();
  const onSuccess = useAuthSuccess();
  const providers = useQuery({ queryKey: ['auth', 'providers'], queryFn: authApi.providers, staleTime: Infinity });
  const google = useMutation({
    mutationFn: (idToken: string) => authApi.google(idToken, currentLanguage()),
    onSuccess,
  });
  const clientId = providers.data?.googleClientId;
  if (!clientId) return null;
  return (
    <div className="mt-5 space-y-3">
      <div className="flex items-center gap-3 text-xs text-slate-400 uppercase">
        <span className="h-px flex-1 bg-slate-200 dark:bg-slate-700" />
        {t('auth.or')}
        <span className="h-px flex-1 bg-slate-200 dark:bg-slate-700" />
      </div>
      {google.isError && <Alert>{translateError(t, google.error)}</Alert>}
      <GoogleButton clientId={clientId} onCredential={(token) => google.mutate(token)} />
    </div>
  );
}

export function LoginPage() {
  const { t } = useTranslation();
  const status = useAuthStore((s) => s.status);
  const onSuccess = useAuthSuccess();
  const [form, setForm] = useState({ email: '', password: '' });
  const login = useMutation({ mutationFn: authApi.login, onSuccess });

  if (status === 'authenticated') return <Navigate to="/" replace />;

  const submit = (e: FormEvent) => {
    e.preventDefault();
    login.mutate(form);
  };

  return (
    <AuthShell title={t('auth.loginTitle')}>
      <form onSubmit={submit} className="space-y-4" noValidate>
        {login.isError && <Alert>{translateError(t, login.error)}</Alert>}
        <Input
          label={t('auth.email')}
          name="email"
          type="email"
          autoComplete="email"
          required
          value={form.email}
          onChange={(e) => setForm({ ...form, email: e.target.value })}
        />
        <PasswordInput
          label={t('auth.password')}
          name="password"
          autoComplete="current-password"
          required
          value={form.password}
          onChange={(e) => setForm({ ...form, password: e.target.value })}
        />
        <div className="-mt-2 text-right">
          <Link to="/forgot-password" className="text-sm text-brand-700 hover:underline dark:text-brand-500">
            {t('auth.forgotLink')}
          </Link>
        </div>
        <Button type="submit" className="w-full" loading={login.isPending}>
          {t('auth.login')}
        </Button>
      </form>
      <GoogleSection />
      <p className="mt-6 text-center text-sm text-slate-500">
        {t('auth.noAccount')}{' '}
        <Link to="/register" className="font-medium text-brand-700 hover:underline dark:text-brand-500">
          {t('auth.goRegister')}
        </Link>
      </p>
    </AuthShell>
  );
}

export function RegisterPage() {
  const { t } = useTranslation();
  const status = useAuthStore((s) => s.status);
  const onSuccess = useAuthSuccess();
  const [form, setForm] = useState({ displayName: '', email: '', password: '' });
  const register = useMutation({ mutationFn: authApi.register, onSuccess });

  if (status === 'authenticated') return <Navigate to="/" replace />;

  const fields = register.isError ? fieldErrors(register.error) : {};
  const fieldMsg = (name: string) => (fields[name] ? translateError(t, fields[name]) : undefined);

  const submit = (e: FormEvent) => {
    e.preventDefault();
    register.mutate({ ...form, locale: currentLanguage() });
  };

  return (
    <AuthShell title={t('auth.registerTitle')}>
      <form onSubmit={submit} className="space-y-4" noValidate>
        {register.isError && <Alert>{translateError(t, register.error)}</Alert>}
        <Input
          label={t('auth.displayName')}
          name="displayName"
          autoComplete="name"
          required
          value={form.displayName}
          error={fieldMsg('displayName')}
          onChange={(e) => setForm({ ...form, displayName: e.target.value })}
        />
        <Input
          label={t('auth.email')}
          name="email"
          type="email"
          autoComplete="email"
          required
          value={form.email}
          error={fieldMsg('email')}
          onChange={(e) => setForm({ ...form, email: e.target.value })}
        />
        <PasswordInput
          label={t('auth.password')}
          name="password"
          autoComplete="new-password"
          required
          minLength={8}
          hint={t('auth.passwordHint')}
          value={form.password}
          error={fieldMsg('password')}
          onChange={(e) => setForm({ ...form, password: e.target.value })}
        />
        <Button type="submit" className="w-full" loading={register.isPending}>
          {t('auth.register')}
        </Button>
      </form>
      <GoogleSection />
      <p className="mt-6 text-center text-sm text-slate-500">
        {t('auth.haveAccount')}{' '}
        <Link to="/login" className="font-medium text-brand-700 hover:underline dark:text-brand-500">
          {t('auth.goLogin')}
        </Link>
      </p>
    </AuthShell>
  );
}
