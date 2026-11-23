# Saver — Frontend

[🇪🇸 Español](README.md) · 🇬🇧 English

Responsive web app for **Saver**, to manage expenses, income and savings and to simulate investments.
Part of the [Saver](../README.en.md) repository. The API is in [`../backend`](../backend/README.en.md), which also documents the [business rules](../backend/docs/en/domain.md).

| | |
|---|---|
| UI | React 19 · TypeScript · Vite |
| Styling | Tailwind CSS 4 (light/dark mode) |
| State | Zustand (session and preferences) · TanStack Query (server data) |
| HTTP | Axios with automatic session renewal |
| Languages | i18next / react-i18next — Spanish and English, auto-detected |
| Charts | Recharts |
| Tests | Vitest |
| Deployment | Firebase Hosting |

## Quick start

Requirements: Node 20+ and the backend running at `http://localhost:8080`.

```bash
npm install
npm run dev        # http://localhost:5173
```

In development, Vite proxies `/api` to the backend (same origin, no CORS).

## Scripts

| Command | Description |
|---|---|
| `npm run dev` | Development server |
| `npm run build` | Type check + production build into `dist/` |
| `npm run typecheck` | Type check only |
| `npm test` | Tests with Vitest |
| `npm run preview` | Serve the build locally |

## Structure

```
src/
├── components/
│   ├── layout/    # AppLayout (sidebar / bottom bar), LanguageSwitcher
│   ├── ui/        # Button, Input, Select, Card, Alert, Spinner, Modal, Tabs, Badge
│   ├── charts/    # Chart theme and tooltip (Recharts)
│   └── finance/   # Increase/end dialogs, increase rule, stats
├── features/
│   ├── auth/        # Login, sign-up, Google, password recovery
│   ├── accounts/    # Accounts, net worth, transfers, adjustments, cards, rates
│   ├── payments/    # Pay (partial payments, account, rate, receipts)
│   ├── categories/  # Categories (useCategories, panel)
│   ├── incomes/     # Income page and form
│   ├── expenses/    # Month expenses, templates, form, due dates
│   ├── history/     # History, filters, real values and month comparison
│   ├── inflation/   # Inflation per currency (Settings panel)
│   ├── timeline/    # Home past + projection chart
│   ├── projections/ # N-month projection
│   └── months/      # Month view, summary, charts
├── i18n/          # Setup and language files (es.json, en.json)
├── lib/           # api (axios), errors, format (currency/dates), month (YYYY-MM)
├── pages/         # Dashboard, Settings
├── stores/        # authStore, uiStore (Zustand)
├── App.tsx        # Routes
└── main.tsx       # Providers
```

## Documentation

| Topic | Description |
|---|---|
| [Architecture](docs/en/architecture.md) | Structure, routes and session flow |
| [State and data](docs/en/state-and-data.md) | When to use Zustand vs. TanStack Query |
| [Internationalization](docs/en/i18n.md) | Languages, detection and how to add texts |
| [Styling](docs/en/styling.md) | Tailwind, theme, responsive and print |
| [Deployment](docs/en/deployment.md) | Firebase Hosting and API connection |
| [Conventions](docs/en/conventions.md) | Code, commits and branches |
