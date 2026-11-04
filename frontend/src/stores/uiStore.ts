import { create } from 'zustand';
import { persist, createJSONStorage } from 'zustand/middleware';

export type Theme = 'system' | 'light' | 'dark';

type UiState = {
  theme: Theme;
  setTheme: (theme: Theme) => void;
};

/** Preferencias locales del dispositivo (no se sincronizan con el servidor). */
export const useUiStore = create<UiState>()(
  persist(
    (set) => ({
      theme: 'system',
      setTheme: (theme) => set({ theme }),
    }),
    {
      name: 'saver.ui',
      storage: createJSONStorage(() => localStorage),
    },
  ),
);

/** Aplica la clase .dark al <html> según preferencia y el sistema operativo. */
export function applyTheme(theme: Theme) {
  const prefersDark = window.matchMedia('(prefers-color-scheme: dark)').matches;
  const dark = theme === 'dark' || (theme === 'system' && prefersDark);
  document.documentElement.classList.toggle('dark', dark);
}

type ProjectionPrefs = {
  months: number;
  startBalance: string;
  setMonths: (m: number) => void;
  setStartBalance: (v: string) => void;
};

/** Parámetros de la proyección recordados en este dispositivo. La inflación vive en el perfil (backend). */
export const useProjectionPrefs = create<ProjectionPrefs>()(
  persist(
    (set) => ({
      months: 12,
      startBalance: '',
      setMonths: (months) => set({ months }),
      setStartBalance: (startBalance) => set({ startBalance }),
    }),
    { name: 'saver.projection', storage: createJSONStorage(() => localStorage) },
  ),
);
