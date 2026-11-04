# Estilos

[English](../en/styling.md) · [← Volver](../../README.md)

## Tailwind CSS 4

Configurado con el plugin `@tailwindcss/vite`; no hay `tailwind.config.js`. El tema se define en `src/index.css`:

```css
@theme {
  --color-brand-700: #0f766e;   /* color principal */
  …
}
```

Usar `brand-*` para acciones y acentos, y la escala `slate-*` para textos, bordes y fondos.

## Modo oscuro

- Estrategia por clase: `.dark` en `<html>` (`@custom-variant dark`).
- El usuario elige **Sistema / Claro / Oscuro** en Ajustes (`uiStore.theme`). Con “Sistema” se sigue `prefers-color-scheme` en vivo.
- Todo componente nuevo debe definir sus variantes `dark:`.

## Responsive

Enfoque **mobile first**: los estilos base son para celular y se amplían con `sm:` (640px), `lg:` (1024px), etc.
- Contenido con ancho máximo `max-w-6xl` y márgenes `px-4 sm:px-6`.
- La barra inferior móvil respeta `env(safe-area-inset-bottom)` (iPhone).
- Tablas extensas: en móvil se mostrarán como tarjetas.

## Gráficos

Recharts con la paleta de `components/charts/chartTheme.ts` (azul = ingresos/serie única, naranja = gastos), validada para daltonismo y con variantes para modo oscuro. Reglas: un solo eje Y, barras finas con extremos redondeados, grilla tenue, leyenda cuando hay 2 series o más, tooltip al pasar el mouse y textos siempre en tinta neutra (nunca del color de la serie). Magnitudes por categoría: un solo color, ordenadas de mayor a menor.

## Impresión (informes)

`index.css` incluye `@media print`. Agregar la clase `no-print` a todo lo que no deba imprimirse (navegación, botones). Las páginas de informe usarán esta base.

## Accesibilidad

- Controles de formulario siempre con `<label>` (los componentes `Input` y `Select` lo resuelven).
- Foco visible (`focus-visible:outline`).
- `aria-invalid`, `role="alert"`, `aria-pressed` donde corresponde.
- Contraste AA en modo claro y oscuro.
