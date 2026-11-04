# API

[Español](../es/api.md) · [← Back](../../README.en.md)

The full interactive reference is in **Swagger UI**: `http://localhost:8080/swagger-ui.html` (JSON at `/v3/api-docs`).

## Conventions

- `/api` prefix. UTF-8 JSON.
- Authentication with `Authorization: Bearer <accessToken>`, except `/api/auth/**`.
- Amounts as decimal strings or numbers with 2 decimals; ISO-8601 dates (`2026-10-25`, `2026-10-21T20:00:00Z`).
- Ids are UUIDs.
- `Accept-Language` is sent but currently only used for reports.

## Endpoints (stage 1: authentication and profile)

| Method | Path | Auth | Description |
|---|---|---|---|
| GET | `/api/auth/providers` | — | Enabled login methods (`googleClientId`) |
| POST | `/api/auth/register` | — | Creates the account. Body: `email`, `password`, `displayName`, `locale` |
| POST | `/api/auth/login` | — | Body: `email`, `password` |
| POST | `/api/auth/google` | — | Body: `idToken`, `locale` |
| POST | `/api/auth/refresh` | cookie | Rotates the refresh token and returns a new access token |
| POST | `/api/auth/logout` | cookie | Ends the current session (204) |
| GET | `/api/me` | Bearer | User profile |
| PATCH | `/api/me` | Bearer | Updates `displayName`, `locale` (`es`/`en`), `baseCurrency` (`ARS`/`USD`), `timezone` |
| GET | `/actuator/health` | — | Service health |

Authentication response:

```json
{
  "accessToken": "eyJhbGciOiJIUzI1NiJ9…",
  "expiresIn": 900,
  "user": {
    "id": "6f1c…",
    "email": "nico@example.com",
    "displayName": "Nico",
    "locale": "es",
    "baseCurrency": "ARS",
    "timezone": "America/Argentina/Buenos_Aires",
    "hasPassword": true,
    "googleLinked": false
  }
}
```

## Endpoints (stage 2)

| Method | Path | Description |
|---|---|---|
| GET | `/api/categories?includeDeleted=` | System + own categories. `includeDeleted=true` also returns deleted ones (history) |
| POST | `/api/categories` | Create. Body: `name`, `color` (`#rrggbb`), `icon?` |
| PATCH | `/api/categories/{id}` | Edit name/color. System ones → 403 |
| DELETE | `/api/categories/{id}` | Soft delete (204) |
| GET | `/api/incomes` | Income with amount versions |
| POST | `/api/incomes` | Body: `description`, `kind` (`FIXED`/`EXTRA`), `currency`, `amount`, `startMonth` (`YYYY-MM`), `months?` (1 = one-off, N = repeats N months, missing = indefinite) |
| PATCH | `/api/incomes/{id}` | `description?`, `endMonth?`, `clearEnd?` |
| POST | `/api/incomes/{id}/amounts` | New amount from a month: `effectiveMonth`, `amount` |
| DELETE | `/api/incomes/{id}` | Delete (204) |
| GET | `/api/expenses` | Expense templates (`currentAmount`, `remainingInstallments`, `nextAdjustmentMonth`, `totalAmount`…) |
| POST | `/api/expenses` | Body: `description`, `categoryId`, `kind` (`ONE_TIME`/`INSTALLMENTS`/`RECURRING`), `currency`, `amount` (per month or per installment), `startMonth`, `installments?`, `dueDay?` (null = last day), `variableAmount`, `adjustEveryMonths?`, `adjustPercent?`, `adjustWithInflation`, `notes?` |
| PATCH | `/api/expenses/{id}` | `description`, `categoryId`, `dueDay` (0 = last day), `variableAmount`, adjustments, `clearAdjustment`, `endMonth`/`clearEnd` (`RECURRING` only), `notes` |
| POST | `/api/expenses/{id}/amounts` | Increase: `effectiveMonth`, `amount`. Recalculates occurrences without payments |
| DELETE | `/api/expenses/{id}` | Deletes the template and its occurrences (409 if it has payments) |
| PATCH | `/api/occurrences/{id}` | One-month adjustment: `expectedAmount?`, `dueDate?`, `notes?` |
| GET | `/api/months/{YYYY-MM\|current}` | Month view: `totals` per currency (`income`, `expenses`, `paid`, `pending`, `overdue`, `balance`), `byCategory`, `incomes`, `occurrences`, `overdue`. Future months: `projected=true` |

Occurrence status in responses: `PENDING`, `PARTIAL`, `PAID` or `OVERDUE` (computed: past due and unpaid).

## Endpoints (password recovery, history and projections)

| Method | Path | Auth | Description |
|---|---|---|---|
| POST | `/api/auth/password/forgot` | — | Body: `email`. Always 204; if the account exists, sends the email with the link |
| POST | `/api/auth/password/reset` | — | Body: `token`, `password`. 204 and signs out every session |
| GET | `/api/history?from=&to=&categories=&currency=` | Bearer | Monthly series (`months`: `income`, filtered `expenses`, `expensesTotal`, `savings`, `byCategory`) and `summary`. Defaults to the last 12 months |
| GET | `/api/history/compare?from=&to=&currency=` | Bearer | Compares two months: `income`, `expenses`, `savings` (`from`, `to`, `diff`, `percent`), `averageSavings`, per-expense `items` (`status`: `UP`/`DOWN`/`SAME`/`NEW`/`ENDED`) and `categories` |
| GET | `/api/projections?months=&startBalance=&inflationPercent=&currency=` | Bearer | Projection from the current month: `series` (income, installments, fixed, one-off, `balance`, `cumulative`, `cumulativeReal`, `inflationPercent`, `inflationOfficial`, `events`), `inflation` (rate used, `source`: `SCREEN`/`MANUAL`/`ESTIMATED`/`NONE`, accumulated) and `summary`. `inflationPercent` changes inflation for that simulation only |

## Endpoints (inflation and timeline)

| Method | Path | Auth | Description |
|---|---|---|---|
| GET | `/api/inflation` | Bearer | Per currency (ARS, USD): latest official figure, last 12 months, estimate, manual rate, projected rate and its source, last 13 months |
| PUT | `/api/inflation/{currency}/manual` | Bearer | Body: `monthlyPercent` (0–50). Manual inflation for months without official data |
| DELETE | `/api/inflation/{currency}/manual` | Bearer | Back to the estimate. 204 |
| GET | `/api/timeline?from=&months=&currency=` | Bearer | Months from `from` (default: 6 before the current one) for `months` (1–120, default 13): `points` with `type` `PAST`/`CURRENT`/`FUTURE`, `income`, `expenses`, `balance`; `current` and `today` |

`GET /api/history` adds `inflationPercent`, `inflationOfficial` and `realFactor` per month, and `inflationPercent`, `inflationEstimated`, `totalSavingsReal`, `expensesRealPercent` to `summary`. `GET /api/history/compare` adds `inflationPercent`, `inflationEstimated`, `savingsReal`, `expensesRealPercent` and `realPercent` per expense.
Income (`POST`/`PATCH /api/incomes`) accepts `adjustEveryMonths`, `adjustPercent`, `adjustWithInflation` (and `clearAdjustment` on `PATCH`); the response includes `nextAdjustmentMonth`.


## Errors

[RFC 9457](https://www.rfc-editor.org/rfc/rfc9457) format (`application/problem+json`) with a `code` property:

```json
{
  "type": "about:blank",
  "title": "Bad Request",
  "status": 400,
  "detail": "validation.failed",
  "code": "validation.failed",
  "fields": { "email": "validation.email", "password": "auth.password_length" }
}
```

| `code` | HTTP | When |
|---|---|---|
| `validation.failed` | 400 | Invalid body; details in `fields` |
| `request.malformed` | 400 | Malformed JSON |
| `auth.invalid_credentials` | 401 | Wrong email or password |
| `auth.email_taken` | 409 | Email already registered |
| `auth.password_length` | 400 | Password outside 8 characters – 72 bytes |
| `auth.unauthorized` | 401 | Missing or invalid access token |
| `auth.forbidden` | 403 | Not allowed |
| `auth.refresh_missing` / `refresh_invalid` / `refresh_expired` | 401 | Refresh cookie problems |
| `auth.google_disabled` / `google_invalid_token` / `google_email_not_verified` | 400/401 | Google sign-in |
| `user.timezone.invalid` | 400 | Unknown time zone |
| `month.invalid` | 400 | Month not in `YYYY-MM` format |
| `category.not_found` / `category.name_taken` / `category.system_readonly` / `category.deleted` / `category.color_invalid` | 404/409/403/400 | Categories |
| `income.not_found` / `income.end_before_start` / `income.amount_before_start` | 404/400 | Income |
| `expense.not_found` / `expense.installments_required` / `expense.adjust_only_recurring` / `expense.end_only_recurring` / `expense.end_before_start` / `expense.amount_before_start` | 404/400 | Expenses |
| `expense.has_payments` | 409 | An expense with payments can't be deleted: end it instead |
| `occurrence.not_found` | 404 | Occurrence missing or owned by another user |
| `validation.*` (`required`, `too_long`, `positive`, `amount`, `min`, `max`, `currency`) | 400 | Inside `fields` |
| `auth.reset_invalid` / `auth.reset_expired` | 400 | Recovery link invalid, already used or expired |
| `history.invalid_range` / `history.range_too_long` | 400 | Invalid period or longer than 120 months |
| `projection.invalid_months` / `projection.invalid_inflation` | 400 | Horizon outside 1–120 or inflation outside 0–50% |
| `inflation.invalid_currency` | 400 | Currency other than ARS/USD |
| `timeline.invalid_months` / `timeline.invalid_from` | 400 | Count outside 1–120 or start month more than 120 months away |
| `income.adjust_only_recurring` | 400 | Increase rule on a one-month income |
| `server.error` | 500 | Unexpected error (logged) |

The frontend translates each `code` using the key `errors.<code>`. **When adding a new code, add it to the frontend language files too.**
