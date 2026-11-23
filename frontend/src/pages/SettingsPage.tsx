import { useState, type FormEvent } from 'react';
import { useMutation } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import { Alert, Button, Card, Input, Select } from '@/components/ui';
import { authApi, type User } from '@/features/auth/authApi';
import { InflationPanel } from '@/features/inflation/InflationPanel';
import { NotificationSettings } from '@/features/notifications/NotificationSettings';
import { SUPPORTED_LANGUAGES } from '@/i18n';
import { translateError } from '@/lib/errors';
import { useAuthStore } from '@/stores/authStore';
import { useUiStore, type Theme } from '@/stores/uiStore';

const TIMEZONES = ['America/Argentina/Buenos_Aires', 'America/Montevideo', 'America/Santiago', 'America/Sao_Paulo', 'America/New_York', 'Europe/Madrid', 'UTC'];
const THEMES: Theme[] = ['system', 'light', 'dark'];

export function SettingsPage() {
  const { t } = useTranslation();
  const user = useAuthStore((s) => s.user) as User;
  const setUser = useAuthStore((s) => s.setUser);
  const theme = useUiStore((s) => s.theme);
  const setTheme = useUiStore((s) => s.setTheme);

  const [form, setForm] = useState({
    displayName: user.displayName,
    locale: user.locale,
    baseCurrency: user.baseCurrency,
    timezone: user.timezone,
  });

  const save = useMutation({ mutationFn: authApi.updateMe, onSuccess: setUser });

  const submit = (e: FormEvent) => {
    e.preventDefault();
    save.mutate(form);
  };

  return (
    <div className="space-y-6">
      <h1 className="text-2xl font-bold">{t('settings.title')}</h1>

      <Card>
        <h2 className="mb-4 font-semibold">{t('settings.profile')}</h2>
        <form onSubmit={submit} className="grid gap-4 sm:grid-cols-2">
          {(save.isError || save.isSuccess) && (
            <div className="sm:col-span-2">
              {save.isError && <Alert>{translateError(t, save.error)}</Alert>}
              {save.isSuccess && <Alert kind="success">{t('settings.saved')}</Alert>}
            </div>
          )}
          <Input label={t('auth.displayName')} name="displayName" value={form.displayName} onChange={(e) => setForm({ ...form, displayName: e.target.value })} />
          <Input label={t('auth.email')} name="email" value={user.email} disabled />
          <Select label={t('settings.language')} name="locale" value={form.locale} onChange={(e) => setForm({ ...form, locale: e.target.value as User['locale'] })}>
            {SUPPORTED_LANGUAGES.map((l) => (
              <option key={l} value={l}>{t(`languages.${l}`)}</option>
            ))}
          </Select>
          <Select label={t('settings.currency')} name="baseCurrency" value={form.baseCurrency} onChange={(e) => setForm({ ...form, baseCurrency: e.target.value as User['baseCurrency'] })}>
            <option value="ARS">ARS — Peso argentino</option>
            <option value="USD">USD — US Dollar</option>
          </Select>
          <Select label={t('settings.timezone')} name="timezone" value={form.timezone} onChange={(e) => setForm({ ...form, timezone: e.target.value })}>
            {Array.from(new Set([form.timezone, ...TIMEZONES])).map((tz) => (
              <option key={tz} value={tz}>{tz}</option>
            ))}
          </Select>
          <div className="flex items-end sm:col-span-2">
            <Button type="submit" loading={save.isPending}>{t('settings.save')}</Button>
          </div>
        </form>
      </Card>

      <InflationPanel />

      <section className="space-y-3" aria-labelledby="notifications-heading">
        <h2 id="notifications-heading" className="text-xl font-semibold">{t('settings.notifications.sectionTitle')}</h2>
        <NotificationSettings />
      </section>

      <Card>
        <h2 className="mb-4 font-semibold">{t('settings.theme')}</h2>
        <div className="inline-flex rounded-lg border border-slate-200 p-0.5 dark:border-slate-700" role="radiogroup">
          {THEMES.map((th) => (
            <button
              key={th}
              type="button"
              role="radio"
              aria-checked={theme === th}
              onClick={() => setTheme(th)}
              className={
                theme === th
                  ? 'rounded-md bg-brand-700 px-3 py-1.5 text-sm text-white'
                  : 'rounded-md px-3 py-1.5 text-sm text-slate-600 hover:bg-slate-100 dark:text-slate-300 dark:hover:bg-slate-800'
              }
            >
              {t(`settings.themes.${th}`)}
            </button>
          ))}
        </div>
      </Card>
    </div>
  );
}
