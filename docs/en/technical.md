# Saver: technical analysis

## Overview

Saver contains two independent applications:

```text
Browser
  └─ frontend/ React + TypeScript + Vite
       └─ /api/* ─ development: Vite proxy ──┐
                                             ├─ backend/ Spring Boot
       └─ production: Firebase Hosting ──────┘      └─ PostgreSQL
                                                    └─ Google Identity (optional)
```

The frontend provides the interface. The backend exposes a REST API, enforces authentication and domain rules, and stores financial data, preferences, and sessions in PostgreSQL. Locally Docker Compose starts PostgreSQL, SeaweedFS (S3 API), and Mailpit. The backend runs with Maven or IntelliJ. Receipts use `StorageService` and are served through authenticated requests.

## Components and responsibilities

| Area | Technology | Current responsibility |
|---|---|---|
| `frontend/` | React 19, TypeScript, Vite, Tailwind | Routes, forms, visual preferences, ES/EN text, and API calls. |
| Client state | Zustand + TanStack Query | Zustand holds session/theme; TanStack Query manages server data. |
| `backend/` | Java 21, Spring Boot 3.5 | REST API, validation, security, account and profile rules. |
| Data | PostgreSQL 16, JPA, Flyway | Profile, finance, account, movement, file, and preference data; versioned migrations. |
| Access | HS256 JWT, BCrypt, HttpOnly cookie | Short-lived access token in memory; rotated opaque refresh token in a cookie. |
| External identity | Google Identity Services / Google JWKS | Validates client ID tokens when `GOOGLE_CLIENT_ID` is set. |
| Operations | Docker, Cloud Run, Firebase Hosting | Backend image and SPA delivery. |

## Useful structure

```text
frontend/src/
  features/auth/      screens and authentication client
  features/notifications/ notification preferences
  stores/             session and local preferences
  lib/api.ts          Axios, language and token renewal
  pages/              dashboard and settings
  i18n/               es/en strings
backend/src/main/
  java/com/saver/auth/     registration, login, refresh, Google
  java/com/saver/user/     profile and user persistence
  java/com/saver/security/ filters, JWT and CORS
  resources/db/migration/  Flyway schema
  resources/application.yml variable-based configuration
```

## Implemented flows

### Registration or sign-in

1. The browser sends credentials to `POST /api/auth/register` or `POST /api/auth/login`.
2. The backend validates content, normalizes email, and hashes registration passwords with BCrypt.
3. It returns an access JWT (15 minutes by default) and puts an opaque refresh token in the HttpOnly `__session` cookie (30 days by default).
4. The frontend keeps the JWT only in its in-memory store and uses `Authorization: Bearer ...` for protected routes.

### Session recovery and renewal

At SPA startup, `authStore.bootstrap()` calls `POST /api/auth/refresh`. The browser sends the cookie because Axios uses `withCredentials`. The backend finds only the token SHA-256 hash, revokes it and issues a new one; reuse detection revokes all sessions for the person. The frontend deduplicates refreshes both within and, where supported, across tabs through Web Locks. A protected-route `401` triggers one refresh and retry; failure clears session state and redirects to `/login`.

### Google access and profile

The frontend reads `GET /api/auth/providers` and only renders Google access when a client ID is returned. It sends the Google ID token to `POST /api/auth/google`; the backend verifies signature, expiry, issuer, audience and verified email. Then it links or creates the account and issues the regular session. `GET/PATCH /api/me` are JWT-protected; language, currency and time zone live in `users`, while theme stays local to the browser.

## Current API

| Endpoint | Auth | Purpose |
|---|---:|---|
| `GET /api/auth/providers` | No | Enabled sign-in methods. |
| `POST /api/auth/register`, `/login`, `/google` | No | Start a session. |
| `POST /api/auth/refresh`, `/logout` | Cookie | Renew or end a session. |
| `GET/PATCH /api/me` | JWT | Read/update profile. |
| `GET/PUT /api/notifications/preferences` | JWT | Read/save personal alert and channel preferences; delivery is not active yet. |
| `GET/PUT /api/notifications/preferences` | JWT | Read and save alert/channel preferences; delivery is not active yet. |
| `/api/categories`, `/api/incomes`, `/api/expenses`, `/api/occurrences/{id}` | JWT | Categories, income, expense templates and one-month adjustments (see `backend/docs/en/api.md`). |
| `GET /api/months/{YYYY-MM\|current}` | JWT | Month balance, due dates, overdue items and projected future months. |
| `GET /api/history`, `GET /api/history/compare` | JWT | Monthly history and month comparison. |
| `GET /api/projections` | JWT | N-month projection, nominal and in today's money. |
| `GET /api/inflation`, `PUT/DELETE /api/inflation/{currency}/manual` | JWT | Official inflation per currency (daily download: ArgentinaDatos/INDEC and BLS), estimate and manual rate. |
| `GET /api/timeline` | JWT | Past + projection series for the Home chart. |
| `/api/accounts`, `/api/transfers`, `/api/cards` | JWT | Accounts, balances, net worth, transfers, adjustments and cards. |
| `/api/occurrences/{id}/payments`, `/api/payments/{id}/attachments`, `/api/incomes/{id}/receipts` | JWT | Partial payments with receipts (storage) and income receipts. |
| `GET /api/exchange-rates` | JWT | Dollar rates (dolarapi) and per-use conversion. |
| `POST /api/auth/password/forgot`, `/reset` | No | Password recovery by email (Mailpit locally, free Brevo for real sending). |

Health is at `/actuator/health`, Swagger at `/swagger-ui.html`, and OpenAPI at `/v3/api-docs`.

## Data, integrations and safeguards

Flyway runs `backend/src/main/resources/db/migration` on startup; Hibernate only validates the schema. Add a new `V{n}__description.sql` for every data change—never modify an applied migration. Firebase Hosting forwards `/api/**` to Cloud Run, preserving same-origin behavior. Firebase forwards only the `__session` cookie to Cloud Run. Vite proxies `/api` locally. `StorageService` (port) + `S3StorageService` (adapter) use server-generated UUID keys, size limits and signed URLs of up to 1 hour; the provider is chosen with `STORAGE_PROVIDER` (`s3-compatible` for SeaweedFS/Supabase/R2/interoperable GCS, `s3` for AWS). Receipt endpoints require an authenticated session and verify ownership. The daily refresh-token cleanup needs distributed locking or Cloud Scheduler before multi-instance business jobs are added.

Validate frontend work with `npm run typecheck`, `npm test`, and `npm run build`; validate backend work with `mvn test` or `mvn verify`. Do not put secrets in `VITE_*`, source control, or images; keep local `.env` files untracked.
