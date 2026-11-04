import { useEffect, useRef } from 'react';
import { useTranslation } from 'react-i18next';
import { currentLanguage } from '@/i18n';

type CredentialResponse = { credential: string };

type GoogleIdApi = {
  initialize: (cfg: { client_id: string; callback: (r: CredentialResponse) => void; ux_mode?: 'popup' }) => void;
  renderButton: (el: HTMLElement, opts: Record<string, unknown>) => void;
};

declare global {
  interface Window {
    google?: { accounts: { id: GoogleIdApi } };
  }
}

const GSI_SRC = 'https://accounts.google.com/gsi/client';
let scriptPromise: Promise<void> | null = null;

function loadGsi(): Promise<void> {
  scriptPromise ??= new Promise((resolve, reject) => {
    const s = document.createElement('script');
    s.src = GSI_SRC;
    s.async = true;
    s.defer = true;
    s.onload = () => resolve();
    s.onerror = () => {
      scriptPromise = null;
      reject(new Error('gsi'));
    };
    document.head.appendChild(s);
  });
  return scriptPromise;
}

/** Botón oficial "Sign in with Google" (Google Identity Services). Entrega un ID token al callback. */
export function GoogleButton({ clientId, onCredential }: { clientId: string; onCredential: (idToken: string) => void }) {
  const ref = useRef<HTMLDivElement>(null);
  const { i18n } = useTranslation();
  const callback = useRef(onCredential);
  callback.current = onCredential;

  useEffect(() => {
    let cancelled = false;
    loadGsi()
      .then(() => {
        if (cancelled || !ref.current || !window.google) return;
        window.google.accounts.id.initialize({
          client_id: clientId,
          callback: (r) => callback.current(r.credential),
          ux_mode: 'popup',
        });
        ref.current.innerHTML = '';
        window.google.accounts.id.renderButton(ref.current, {
          theme: 'outline',
          size: 'large',
          shape: 'pill',
          text: 'continue_with',
          width: 320,
          locale: currentLanguage(),
        });
      })
      .catch(() => undefined);
    return () => {
      cancelled = true;
    };
  }, [clientId, i18n.language]);

  return <div ref={ref} className="flex min-h-11 justify-center" />;
}
