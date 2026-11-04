import { useEffect, useState } from 'react';

/**
 * Paleta de gráficos (validada para daltonismo y contraste, ver docs/es/estilos.md).
 * Slot 1 = ingresos / serie única, slot 2 = gastos. Los textos usan tinta neutra, nunca el color de la serie.
 */
const LIGHT = { series1: '#2a78d6', series2: '#eb6834', grid: '#e2e8f0', axis: '#64748b', text: '#0f172a', surface: '#ffffff' };
const DARK = { series1: '#3987e5', series2: '#d95926', grid: '#1e293b', axis: '#94a3b8', text: '#f1f5f9', surface: '#0f172a' };

export type ChartTheme = typeof LIGHT;

/** Sigue la clase .dark del <html> (el tema elegido en Ajustes). */
export function useChartTheme(): ChartTheme {
  const [dark, setDark] = useState(() => document.documentElement.classList.contains('dark'));
  useEffect(() => {
    const obs = new MutationObserver(() => setDark(document.documentElement.classList.contains('dark')));
    obs.observe(document.documentElement, { attributes: true, attributeFilter: ['class'] });
    return () => obs.disconnect();
  }, []);
  return dark ? DARK : LIGHT;
}
