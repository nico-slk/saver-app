import { Suspense, lazy, useEffect, type ReactNode } from 'react';
import { Link, Route, Routes } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { ProtectedLayout } from '@/components/layout/AppLayout';
import { LoginPage, RegisterPage } from '@/features/auth/AuthPages';
import { ForgotPasswordPage, ResetPasswordPage } from '@/features/auth/PasswordPages';
import { InlineLoader } from '@/components/ui/overlay';
import { useAuthStore } from '@/stores/authStore';
import { applyTheme, useUiStore } from '@/stores/uiStore';

// Páginas con carga diferida: los gráficos (Recharts) solo se descargan al entrar al inicio.
const DashboardPage = lazy(() => import('@/pages/DashboardPage').then((m) => ({ default: m.DashboardPage })));
const ExpensesPage = lazy(() => import('@/features/expenses/ExpensesPage').then((m) => ({ default: m.ExpensesPage })));
const HistoryPage = lazy(() => import('@/features/history/HistoryPage').then((m) => ({ default: m.HistoryPage })));
const ProjectionsPage = lazy(() => import('@/features/projections/ProjectionsPage').then((m) => ({ default: m.ProjectionsPage })));
const AccountsPage = lazy(() => import('@/features/accounts/AccountsPage').then((m) => ({ default: m.AccountsPage })));
const SettingsPage = lazy(() => import('@/pages/SettingsPage').then((m) => ({ default: m.SettingsPage })));
const IncomesPage = lazy(() => import('@/features/incomes/IncomesPage').then((m) => ({ default: m.IncomesPage })));

function Page({ children }: { children: ReactNode }) {
  const { t } = useTranslation();
  return <Suspense fallback={<InlineLoader label={t('common.loading')} />}>{children}</Suspense>;
}

function NotFound() {
  const { t } = useTranslation();
  return (
    <div className="grid min-h-[50dvh] place-items-center text-center">
      <div className="space-y-3">
        <p className="text-5xl font-bold text-brand-700">404</p>
        <p>{t('common.notFound')}</p>
        <Link to="/" className="text-brand-700 underline">{t('common.backHome')}</Link>
      </div>
    </div>
  );
}

export default function App() {
  const bootstrap = useAuthStore((s) => s.bootstrap);
  const theme = useUiStore((s) => s.theme);

  useEffect(() => {
    void bootstrap();
  }, [bootstrap]);

  useEffect(() => {
    applyTheme(theme);
    if (theme !== 'system') return;
    const mq = window.matchMedia('(prefers-color-scheme: dark)');
    const onChange = () => applyTheme('system');
    mq.addEventListener('change', onChange);
    return () => mq.removeEventListener('change', onChange);
  }, [theme]);

  return (
    <Routes>
      <Route path="/login" element={<LoginPage />} />
      <Route path="/register" element={<RegisterPage />} />
      <Route path="/forgot-password" element={<ForgotPasswordPage />} />
      <Route path="/reset-password" element={<ResetPasswordPage />} />
      <Route element={<ProtectedLayout />}>
        <Route index element={<Page><DashboardPage /></Page>} />
        <Route path="expenses" element={<Page><ExpensesPage /></Page>} />
        <Route path="incomes" element={<Page><IncomesPage /></Page>} />
        <Route path="accounts" element={<Page><AccountsPage /></Page>} />
        <Route path="history" element={<Page><HistoryPage /></Page>} />
        <Route path="projections" element={<Page><ProjectionsPage /></Page>} />
        <Route path="settings" element={<Page><SettingsPage /></Page>} />
        <Route path="*" element={<NotFound />} />
      </Route>
    </Routes>
  );
}
