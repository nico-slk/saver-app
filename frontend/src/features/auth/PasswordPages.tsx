import { useState, type FormEvent } from 'react';
import { Link, useNavigate, useSearchParams } from 'react-router-dom';
import { useMutation } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import { Alert, Button, Input } from '@/components/ui';
import { fieldErrors, translateError } from '@/lib/errors';
import { authApi } from './authApi';
import { AuthShell } from './AuthPages';

const backToLogin = 'font-medium text-brand-700 hover:underline dark:text-brand-500';

/** Pide el email y envía el link. El mensaje es el mismo exista o no la cuenta. */
export function ForgotPasswordPage() {
  const { t } = useTranslation();
  const [email, setEmail] = useState('');
  const send = useMutation({ mutationFn: () => authApi.forgotPassword(email) });
  const fields = send.isError ? fieldErrors(send.error) : {};

  const submit = (e: FormEvent) => {
    e.preventDefault();
    send.mutate();
  };

  return (
    <AuthShell title={t('auth.forgotTitle')}>
      {send.isSuccess ? (
        <div className="space-y-4">
          <Alert kind="success">{t('auth.forgotSent', { email })}</Alert>
          <p className="text-sm text-slate-500">{t('auth.forgotSentHint')}</p>
        </div>
      ) : (
        <form onSubmit={submit} className="space-y-4" noValidate>
          <p className="text-sm text-slate-500">{t('auth.forgotIntro')}</p>
          {send.isError && !fields.email && <Alert>{translateError(t, send.error)}</Alert>}
          <Input label={t('auth.email')} name="email" type="email" autoComplete="email" required value={email}
            error={fields.email ? translateError(t, fields.email) : undefined}
            onChange={(e) => setEmail(e.target.value)} />
          <Button type="submit" className="w-full" loading={send.isPending}>{t('auth.forgotSubmit')}</Button>
        </form>
      )}
      <p className="mt-6 text-center text-sm">
        <Link to="/login" className={backToLogin}>{t('auth.backToLogin')}</Link>
      </p>
    </AuthShell>
  );
}

/** Llega desde el link del email (?token=...). Al terminar se cierran las demás sesiones. */
export function ResetPasswordPage() {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const [params] = useSearchParams();
  const token = params.get('token') ?? '';
  const [form, setForm] = useState({ password: '', confirm: '' });
  const [mismatch, setMismatch] = useState(false);
  const reset = useMutation({ mutationFn: () => authApi.resetPassword(token, form.password) });
  const fields = reset.isError ? fieldErrors(reset.error) : {};

  const submit = (e: FormEvent) => {
    e.preventDefault();
    if (form.password !== form.confirm) {
      setMismatch(true);
      return;
    }
    setMismatch(false);
    reset.mutate();
  };

  if (!token) {
    return (
      <AuthShell title={t('auth.resetTitle')}>
        <Alert>{t('errors.auth.reset_invalid')}</Alert>
        <p className="mt-6 text-center text-sm">
          <Link to="/forgot-password" className={backToLogin}>{t('auth.requestNewLink')}</Link>
        </p>
      </AuthShell>
    );
  }

  return (
    <AuthShell title={t('auth.resetTitle')}>
      {reset.isSuccess ? (
        <div className="space-y-4">
          <Alert kind="success">{t('auth.resetDone')}</Alert>
          <Button className="w-full" onClick={() => navigate('/login', { replace: true })}>{t('auth.goLogin')}</Button>
        </div>
      ) : (
        <form onSubmit={submit} className="space-y-4" noValidate>
          {reset.isError && !fields.password && (
            <Alert>
              {translateError(t, reset.error)}{' '}
              <Link to="/forgot-password" className="underline">{t('auth.requestNewLink')}</Link>
            </Alert>
          )}
          <Input label={t('auth.newPassword')} name="password" type="password" autoComplete="new-password" required minLength={8}
            hint={t('auth.passwordHint')} value={form.password}
            error={fields.password ? translateError(t, fields.password) : undefined}
            onChange={(e) => setForm({ ...form, password: e.target.value })} />
          <Input label={t('auth.confirmPassword')} name="confirm" type="password" autoComplete="new-password" required
            value={form.confirm} error={mismatch ? t('auth.passwordMismatch') : undefined}
            onChange={(e) => setForm({ ...form, confirm: e.target.value })} />
          <Button type="submit" className="w-full" loading={reset.isPending}>{t('auth.resetSubmit')}</Button>
        </form>
      )}
    </AuthShell>
  );
}
