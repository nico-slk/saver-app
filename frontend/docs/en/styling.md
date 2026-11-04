# Styling

[Español](../es/estilos.md) · [← Back](../../README.en.md)

## Tailwind CSS 4

Set up with the `@tailwindcss/vite` plugin; there is no `tailwind.config.js`. The theme is defined in `src/index.css`:

```css
@theme {
  --color-brand-700: #0f766e;   /* primary color */
  …
}
```

Use `brand-*` for actions and accents, and the `slate-*` scale for text, borders and backgrounds.

## Dark mode

- Class strategy: `.dark` on `<html>` (`@custom-variant dark`).
- Users choose **System / Light / Dark** in Settings (`uiStore.theme`). "System" follows `prefers-color-scheme` live.
- Every new component must define its `dark:` variants.

## Responsive

**Mobile first**: base styles target phones and are extended with `sm:` (640px), `lg:` (1024px), etc.
- Content has a `max-w-6xl` max width and `px-4 sm:px-6` gutters.
- The mobile bottom bar respects `env(safe-area-inset-bottom)` (iPhone).
- Large tables: on mobile they will be shown as cards.

## Charts

Recharts with the palette in `components/charts/chartTheme.ts` (blue = income/single series, orange = expenses), validated for color-vision deficiency, with dark-mode variants. Rules: a single Y axis, thin bars with rounded ends, faint grid, a legend whenever there are 2+ series, hover tooltips and text always in neutral ink (never the series color). Per-category magnitudes: a single color, sorted largest first.

## Print (reports)

`index.css` includes `@media print`. Add the `no-print` class to anything that must not be printed (navigation, buttons). Report pages will build on this.

## Accessibility

- Form controls always have a `<label>` (`Input` and `Select` handle it).
- Visible focus (`focus-visible:outline`).
- `aria-invalid`, `role="alert"`, `aria-pressed` where relevant.
- AA contrast in light and dark modes.
