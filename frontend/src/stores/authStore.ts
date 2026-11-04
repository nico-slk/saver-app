import { create } from 'zustand';
import i18n, { SUPPORTED_LANGUAGES } from '@/i18n';
import { bindAuth } from '@/lib/api';
import { authApi, type AuthResponse, type User } from '@/features/auth/authApi';

type Status = 'idle' | 'loading' | 'authenticated' | 'anonymous';

type AuthState = {
  status: Status;
  accessToken: string | null;
  user: User | null;
  /** Intenta recuperar la sesión con la cookie de refresh (al cargar la app). */
  bootstrap: () => Promise<void>;
  setSession: (res: AuthResponse) => void;
  setUser: (user: User) => void;
  refresh: () => Promise<string | null>;
  logout: () => Promise<void>;
  clear: () => void;
};

let inflightRefresh: Promise<string | null> | null = null;

/**
 * Serializa el refresh entre pestañas (Web Locks API): la segunda pestaña espera
 * y usa la cookie ya rotada por la primera, evitando la detección de reutilización.
 */
function withCrossTabLock<T>(fn: () => Promise<T>): Promise<T> {
  if (typeof navigator !== 'undefined' && navigator.locks) {
    return navigator.locks.request('saver-auth-refresh', fn) as Promise<T>;
  }
  return fn();
}

/** El idioma guardado en el perfil manda una vez que el usuario inicia sesión. */
function applyUserLocale(user: User) {
  if ((SUPPORTED_LANGUAGES as readonly string[]).includes(user.locale) && i18n.language !== user.locale) {
    void i18n.changeLanguage(user.locale);
  }
}

export const useAuthStore = create<AuthState>()((set, get) => ({
  status: 'idle',
  accessToken: null,
  user: null,

  bootstrap: async () => {
    if (get().status !== 'idle') return;
    set({ status: 'loading' });
    const token = await get().refresh();
    if (!token) set({ status: 'anonymous' });
  },

  setSession: (res) => {
    applyUserLocale(res.user);
    set({ status: 'authenticated', accessToken: res.accessToken, user: res.user });
  },

  setUser: (user) => {
    applyUserLocale(user);
    set({ user });
  },

  // Deduplicado: el backend rota el refresh token y detecta reutilización,
  // así que nunca deben salir dos /refresh en paralelo con la misma cookie.
  refresh: () => {
    inflightRefresh ??= withCrossTabLock(() => authApi.refresh())
      .then((res) => {
        get().setSession(res);
        return res.accessToken as string | null;
      })
      .catch(() => {
        get().clear();
        return null;
      })
      .finally(() => {
        inflightRefresh = null;
      });
    return inflightRefresh;
  },

  logout: async () => {
    try {
      await authApi.logout();
    } finally {
      get().clear();
    }
  },

  clear: () => set({ status: 'anonymous', accessToken: null, user: null }),
}));

bindAuth({
  getToken: () => useAuthStore.getState().accessToken,
  refresh: () => useAuthStore.getState().refresh(),
  onSessionExpired: () => useAuthStore.getState().clear(),
});
