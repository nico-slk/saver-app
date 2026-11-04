import i18n from 'i18next';
import { initReactI18next } from 'react-i18next';
import LanguageDetector from 'i18next-browser-languagedetector';
import es from './locales/es.json';
import en from './locales/en.json';

export const SUPPORTED_LANGUAGES = ['es', 'en'] as const;
export type Language = (typeof SUPPORTED_LANGUAGES)[number];

/** Locale BCP-47 usado por Intl para formatear montos y fechas. */
export const INTL_LOCALE: Record<Language, string> = { es: 'es-AR', en: 'en-US' };

export function currentLanguage(): Language {
  const lng = (i18n.resolvedLanguage ?? i18n.language ?? 'es').slice(0, 2);
  return (SUPPORTED_LANGUAGES as readonly string[]).includes(lng) ? (lng as Language) : 'es';
}

void i18n
  .use(LanguageDetector)
  .use(initReactI18next)
  .init({
    resources: { es: { translation: es }, en: { translation: en } },
    fallbackLng: 'es',
    supportedLngs: SUPPORTED_LANGUAGES,
    nonExplicitSupportedLngs: true, // es-AR -> es, en-US -> en
    load: 'languageOnly',
    interpolation: { escapeValue: false },
    detection: {
      // 1) elección guardada, 2) idioma del navegador, 3) idioma del <html>
      order: ['localStorage', 'navigator', 'htmlTag'],
      lookupLocalStorage: 'saver.lang',
      caches: ['localStorage'],
    },
  });

i18n.on('languageChanged', (lng) => {
  document.documentElement.lang = lng.slice(0, 2);
});

export default i18n;
