# Architecture

[Español](../es/arquitectura.md) · [← Back](../../README.en.md)

## Principles

- **Feature-based organization**: each domain (`auth`, later `expenses`, `incomes`, `history`…) lives in `src/features/<name>` with its pages, components and API client.
- **Own lightweight UI components** in `src/components/ui`, no component library.
- **`@/` alias** points to `src/` (configured in `vite.config.ts` and `tsconfig.app.json`).
- **Strict TypeScript** (`strict`, `noUncheckedIndexedAccess`, `noUnusedLocals`).

## Routes

| Route | Page | Access |
|---|---|---|
| `/login` | Sign in | Public (redirects to `/` if already signed in) |
| `/register` | Sign up | Public |
| `/forgot-password` | Request a password recovery link | Public |
| `/reset-password?token=` | Choose a new password (email link) | Public |
| `/?from=YYYY-MM&months=N` | Home (dashboard). `from`/`months`: range of the past + projection chart (default 6 months before and 6 after) | Protected |
| `/expenses?tab=month\|templates\|categories&month=YYYY-MM` | Month expenses, fixed & installments, categories | Protected |
| `/incomes` | Income (with "Receive" for the current month) | Protected |
| `/accounts` | Accounts, net worth, cards and rates | Protected |
| `/history?from=&to=&cat=&real=1` | History: period, category filter, inflation-adjusted values and comparison | Protected |
| `/projections` | Projections (horizon and starting balance remembered on the device; inflation in the profile) | Protected |
| `/settings` | Settings (profile, inflation, theme, and notifications) | Protected |
| `*` | 404 | Protected |

Protected routes hang from `ProtectedLayout`. While the session is being restored it shows a loader; with no session it redirects to `/login` and remembers the requested page to return after login.

## Session

```
App load
  └─▶ authStore.bootstrap() → POST /api/auth/refresh (HttpOnly cookie)
        ├─ ok    → stores accessToken + user in memory → status "authenticated"
        └─ error → status "anonymous" → /login

Request returns 401
  └─▶ axios interceptor → refresh (a single shared one) → retries the request
        └─ if refresh fails → session is cleared → /login
```

- The **access token lives only in memory** (never `localStorage`), so an injected script cannot steal a long-lived session.
- The **refresh token** is in an `HttpOnly` cookie the browser sends only to `/api/auth/*`.
- Refreshes are **serialized across tabs** with the Web Locks API: the backend rotates the token on every use and detects reuse.
- The `Authorization` header is never sent to `/api/auth/*`.

## Error handling

The backend responds with a `ProblemDetail` carrying a `code` (`auth.invalid_credentials`). `lib/errors.ts` extracts it and `translateError(t, err)` translates it with the `errors.<code>` key. Field validation errors arrive in `fields`.

## Responsive layout

| Width | Navigation |
|---|---|
| `< 1024px` | Top header (language, settings, log out) + bottom bar: Home, Expenses, Income, Accounts and "More" (History, Projections…) |
| `≥ 1024px` | Fixed sidebar with all sections |

Sections from future stages are shown disabled with a "Soon" label. Pages are lazy-loaded (`React.lazy`): Recharts is only downloaded when a page with charts opens (Settings is lazy too because it shows inflation).

## Financial data

- `GET /api/months/{month}` feeds Home and “This month”; the selected month lives in the URL (`?month=`).
- After creating, editing or deleting income, expenses or categories, `useFinanceInvalidation()` refreshes every related query.
- Amounts are sent as strings (`"15000.50"`) to avoid precision loss.
- Notification preferences are read and saved through `/api/notifications/preferences`; they are not persisted in the browser.

## Payments and receipts

- "Pay" records the payment (`POST /api/occurrences/{id}/payments`) and then uploads the receipts (`multipart/form-data`). They are two requests: if the upload fails, the payment is already recorded, the dialog stays open and files can be attached from the payment (no double payment).
- The axios client defaults to JSON; uploads set `multipart/form-data` and the browser adds the boundary.
- Receipts are opened by downloading them with the session (`blob` → temporary URL): there are no public links.
- Currency conversion is previewed with `GET /api/exchange-rates/convert`, so the rule of which dollar to use lives only in the backend.
