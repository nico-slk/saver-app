import { NavLink, Navigate, Outlet, useLocation } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import clsx from 'clsx';
import { FullScreenLoader } from '@/components/ui';
import { useAuthStore } from '@/stores/authStore';
import { LanguageSwitcher } from './LanguageSwitcher';

type NavItem = { to: string; key: string; icon: string; ready: boolean };

// Iconos SVG (paths de 24x24) para no sumar dependencias.
const ICONS: Record<string, string> = {
  home: 'M3 10.5 12 3l9 7.5V20a1 1 0 0 1-1 1h-5v-6H9v6H4a1 1 0 0 1-1-1z',
  expenses: 'M4 6h16M4 12h16M4 18h10',
  incomes: 'M12 19V5m0 0-6 6m6-6 6 6',
  history: 'M12 8v4l3 2M3 12a9 9 0 1 0 3-6.7L3 8m0-5v5h5',
  projections: 'M3 17l6-6 4 4 8-8m0 0h-5m5 0v5',
  investments: 'M12 3v18m5-14H9.5a3.5 3.5 0 0 0 0 7h5a3.5 3.5 0 0 1 0 7H6',
  reports: 'M7 3h7l5 5v13H7zM14 3v5h5M10 13h6M10 17h6',
  settings: 'M12 15a3 3 0 1 0 0-6 3 3 0 0 0 0 6zm7.4-3a7.4 7.4 0 0 0-.1-1.3l2-1.6-2-3.4-2.4 1a7.6 7.6 0 0 0-2.2-1.3L14.3 3h-4l-.4 2.4a7.6 7.6 0 0 0-2.2 1.3l-2.4-1-2 3.4 2 1.6a7.4 7.4 0 0 0 0 2.6l-2 1.6 2 3.4 2.4-1a7.6 7.6 0 0 0 2.2 1.3l.4 2.4h4l.4-2.4a7.6 7.6 0 0 0 2.2-1.3l2.4 1 2-3.4-2-1.6c.1-.4.1-.9.1-1.3z',
  logout: 'M15 3h4a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2h-4M10 17l5-5-5-5M15 12H3',
};

const NAV: NavItem[] = [
  { to: '/', key: 'dashboard', icon: 'home', ready: true },
  { to: '/expenses', key: 'expenses', icon: 'expenses', ready: true },
  { to: '/incomes', key: 'incomes', icon: 'incomes', ready: true },
  { to: '/history', key: 'history', icon: 'history', ready: true },
  { to: '/projections', key: 'projections', icon: 'projections', ready: true },
  { to: '/investments', key: 'investments', icon: 'investments', ready: false },
  { to: '/reports', key: 'reports', icon: 'reports', ready: false },
  { to: '/settings', key: 'settings', icon: 'settings', ready: true },
];

function Icon({ name, className }: { name: string; className?: string }) {
  return (
    <svg viewBox="0 0 24 24" className={clsx('size-5 shrink-0', className)} fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" aria-hidden>
      <path d={ICONS[name]} />
    </svg>
  );
}

/** Ruta protegida: si no hay sesión redirige a /login recordando a dónde iba. */
export function ProtectedLayout() {
  const { t } = useTranslation();
  const status = useAuthStore((s) => s.status);
  const location = useLocation();

  if (status === 'idle' || status === 'loading') return <FullScreenLoader label={t('common.loading')} />;
  if (status === 'anonymous') return <Navigate to="/login" replace state={{ from: location.pathname }} />;
  return <AppLayout />;
}

function AppLayout() {
  const { t } = useTranslation();
  const user = useAuthStore((s) => s.user);
  const logout = useAuthStore((s) => s.logout);

  const linkClass = (item: NavItem) => ({ isActive }: { isActive: boolean }) =>
    clsx(
      'flex items-center gap-3 rounded-lg px-3 py-2 text-sm font-medium transition',
      !item.ready && 'pointer-events-none opacity-45',
      isActive
        ? 'bg-brand-50 text-brand-800 dark:bg-brand-800/30 dark:text-brand-100'
        : 'text-slate-600 hover:bg-slate-100 dark:text-slate-300 dark:hover:bg-slate-800',
    );

  return (
    <div className="min-h-dvh lg:grid lg:grid-cols-[240px_1fr]">
      {/* Sidebar (desktop) */}
      <aside className="no-print hidden border-r border-slate-200 bg-white px-3 py-5 lg:flex lg:flex-col dark:border-slate-800 dark:bg-slate-900">
        <div className="mb-6 flex items-center gap-2 px-3">
          <img src="/favicon.svg" alt="" className="size-8" />
          <span className="text-lg font-bold">{t('app.name')}</span>
        </div>
        <nav className="flex-1 space-y-1">
          {NAV.map((item) => (
            <NavLink key={item.to} to={item.to} end={item.to === '/'} className={linkClass(item)} aria-disabled={!item.ready}>
              <Icon name={item.icon} />
              <span className="flex-1">{t(`nav.${item.key}`)}</span>
              {!item.ready && (
                <span className="rounded bg-slate-100 px-1.5 py-0.5 text-[10px] text-slate-500 dark:bg-slate-800" title={t('nav.comingSoon')}>
                  {t('nav.soon')}
                </span>
              )}
            </NavLink>
          ))}
        </nav>
        <div className="space-y-3 border-t border-slate-200 px-3 pt-4 dark:border-slate-800">
          <p className="truncate text-sm text-slate-500" title={user?.email}>{user?.displayName}</p>
          <LanguageSwitcher compact />
          <button type="button" onClick={() => void logout()} className="flex items-center gap-2 text-sm text-slate-600 hover:text-red-600 dark:text-slate-300">
            <Icon name="logout" className="size-4" /> {t('nav.logout')}
          </button>
        </div>
      </aside>

      {/* Header (mobile) */}
      <header className="no-print sticky top-0 z-10 flex items-center justify-between border-b border-slate-200 bg-white/90 px-4 py-3 backdrop-blur lg:hidden dark:border-slate-800 dark:bg-slate-900/90">
        <div className="flex items-center gap-2">
          <img src="/favicon.svg" alt="" className="size-7" />
          <span className="font-bold">{t('app.name')}</span>
        </div>
        <div className="flex items-center gap-2">
          <LanguageSwitcher compact />
          <NavLink to="/settings" aria-label={t('nav.settings')} className={({ isActive }) => clsx('rounded-lg p-2 hover:bg-slate-100 dark:hover:bg-slate-800', isActive ? 'text-brand-700 dark:text-brand-500' : 'text-slate-600 dark:text-slate-300')}>
            <Icon name="settings" />
          </NavLink>
          <button type="button" onClick={() => void logout()} aria-label={t('nav.logout')} className="rounded-lg p-2 text-slate-600 hover:bg-slate-100 dark:text-slate-300 dark:hover:bg-slate-800">
            <Icon name="logout" />
          </button>
        </div>
      </header>

      <main className="mx-auto w-full max-w-6xl px-4 pt-6 pb-24 sm:px-6 lg:pb-10">
        <Outlet />
      </main>

      {/* Bottom nav (mobile): solo secciones principales */}
      <nav className="no-print fixed inset-x-0 bottom-0 z-10 grid grid-cols-5 border-t border-slate-200 bg-white pb-[env(safe-area-inset-bottom)] lg:hidden dark:border-slate-800 dark:bg-slate-900">
        {NAV.filter((n) => ['dashboard', 'expenses', 'incomes', 'history', 'projections'].includes(n.key)).map((item) => (
          <NavLink
            key={item.to}
            to={item.to}
            end={item.to === '/'}
            aria-disabled={!item.ready}
            className={({ isActive }) =>
              clsx(
                'flex flex-col items-center gap-0.5 py-2 text-[11px]',
                !item.ready && 'pointer-events-none opacity-40',
                isActive ? 'text-brand-700 dark:text-brand-500' : 'text-slate-500',
              )
            }
          >
            <Icon name={item.icon} />
            {t(`nav.${item.key}`)}
          </NavLink>
        ))}
      </nav>
    </div>
  );
}
