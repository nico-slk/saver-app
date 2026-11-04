# Architecture

[Español](../es/arquitectura.md) · [← Back](../../README.en.md)

## Overview

```
┌──────────────────────┐   HTTPS /api/*    ┌──────────────────────────┐
│  saver-frontend      │ ────────────────▶ │  saver-backend (Spring)  │
│  React SPA           │ ◀──────────────── │  Cloud Run               │
│  Firebase Hosting    │   JSON / Problem  └──────┬─────────┬─────────┘
└──────────────────────┘                          │         │
                                                  ▼         ▼
                                      PostgreSQL (Supabase)   S3 object storage
                                                              (SeaweedFS locally)
```

- **Stateless**: there is no HTTP session. Every request is authenticated with a JWT access token.
- **Isolated multi-user**: every resource belongs to a `user_id` and every query is filtered by the authenticated user (`@CurrentUser`). A `userId` coming from the body or the URL is never trusted.
- **Language**: the backend does not return translated text. Errors carry a stable `code` (`auth.invalid_credentials`) that the frontend translates. PDF reports will use the profile `locale`.

## Packages

Code is organized **by feature**, not by technical layer. Each package contains its controller, service, entities, repositories and DTOs.

| Package | Responsibility |
|---|---|
| `auth` | Sign-up, login, Google, token issuing and rotation |
| `user` | Profile and preferences (`/api/me`) |
| `security` | Filter chain, JWT encoder/decoder, `@CurrentUser` annotation |
| `config` | `AppProperties` (`app.*` prefix), injectable `Clock` |
| `common` | `ApiException`, `GlobalExceptionHandler` |
| `storage` | `StorageService` port and S3 adapter (SeaweedFS locally; configurable production provider). UUID keys, signed URLs |

Packages planned for upcoming stages: `category`, `income`, `expense`, `payment`, `attachment`, `closing`, `exchange`, `projection`, `investment`, `report`.

## Technical decisions

| Decision | Reason |
|---|---|
| Amounts as `BigDecimal` / `NUMERIC(15,2)` | Avoid `double` rounding errors |
| Timestamps as `Instant` / `timestamptz` (UTC) | The "month" is computed in the user's time zone (default `America/Argentina/Buenos_Aires`) |
| Injectable `Clock` | Test due dates, month closings and token expiry |
| Flyway + `ddl-auto: validate` | Schema is versioned in SQL; Hibernate only validates it matches |
| RFC 9457 errors (`ProblemDetail`) | Standard format with `code` for i18n and `fields` for validation |
| Attachments in object storage behind a port | Only metadata goes to the DB (base64 would inflate it ~33%). The `StorageService` interface lets the provider change without touching the domain |
| Records for DTOs and properties | Immutability and less code |
| No Lombok | Less compile-time magic; records cover most cases |

## Scheduled jobs

`@EnableScheduling` is enabled. Current and planned jobs:

| Job | When | Stage |
|---|---|---|
| Purge expired refresh tokens | Daily 03:30 (ART) | 1 ✅ |
| Automatic month closing | End of each month, per user time zone | 3 |
| USD exchange rate update | Daily | 3 |

On Cloud Run, if scaled beyond one instance, jobs must be coordinated (ShedLock or Cloud Scheduler calling an internal endpoint).
