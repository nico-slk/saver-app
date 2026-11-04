import { useTranslation } from 'react-i18next';
import clsx from 'clsx';
import { SUPPORTED_LANGUAGES, currentLanguage, type Language } from '@/i18n';
import { authApi } from '@/features/auth/authApi';
import { useAuthStore } from '@/stores/authStore';

/** Cambia el idioma; si hay sesión, lo guarda también en el perfil. */
export function LanguageSwitcher({ compact = false }: { compact?: boolean }) {
  const { t, i18n } = useTranslation();
  const user = useAuthStore((s) => s.user);
  const setUser = useAuthStore((s) => s.setUser);
  const active = currentLanguage();

  const change = async (lng: Language) => {
    await i18n.changeLanguage(lng);
    if (user && user.locale !== lng) {
      try {
        setUser(await authApi.updateMe({ locale: lng }));
      } catch {
        /* el cambio local ya se aplicó; se reintenta en el próximo cambio */
      }
    }
  };

  return (
    <div className="inline-flex rounded-lg border border-slate-200 p-0.5 dark:border-slate-700" role="group" aria-label={t('settings.language')}>
      {SUPPORTED_LANGUAGES.map((lng) => (
        <button
          key={lng}
          type="button"
          onClick={() => void change(lng)}
          aria-pressed={active === lng}
          className={clsx(
            'rounded-md px-2.5 py-1 text-xs font-medium uppercase transition',
            active === lng
              ? 'bg-brand-700 text-white'
              : 'text-slate-600 hover:bg-slate-100 dark:text-slate-300 dark:hover:bg-slate-800',
          )}
        >
          {compact ? lng : t(`languages.${lng}`)}
        </button>
      ))}
    </div>
  );
}
