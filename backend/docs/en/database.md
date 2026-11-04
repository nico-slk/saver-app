# Database

[Español](../es/base-de-datos.md) · [← Back](../../README.en.md)

PostgreSQL 16. Local in Docker; in production, Supabase (or Cloud SQL).

## Conventions

| Element | Rule |
|---|---|
| Tables and columns | `snake_case`, plural table names (`users`, `refresh_tokens`) |
| Primary key | `id UUID`, generated in Java |
| Ownership | Every user-data table has `user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE` + index |
| Timestamps | `TIMESTAMPTZ` (UTC) |
| Dates without time (due dates, months) | `DATE`; a month is stored as the `DATE` of day 1 |
| Amounts | `NUMERIC(15,2)` · exchange rates `NUMERIC(15,4)` |
| Currencies | `VARCHAR(3)` with `CHECK` (`ARS`, `USD`) |
| Enums | `VARCHAR` + `CHECK`, mapped with `@Enumerated(EnumType.STRING)` |
| Text | `VARCHAR(n)` (Hibernate validates `varchar`, not `text`) |
| Soft delete | `deleted_at TIMESTAMPTZ NULL` where history requires it (e.g. categories) |
| Audit | `created_at`, `updated_at` |

## Migrations (Flyway)

- Location: `src/main/resources/db/migration`.
- Name: `V{n}__description_in_snake_case.sql` (e.g. `V2__categories_and_incomes.sql`).
- **Never edit an applied migration**: create a new one.
- Hibernate runs with `ddl-auto: validate`: if an entity does not match the schema, the app will not start.

## Current schema

```
users
  id UUID PK
  email VARCHAR(320)          -- unique (lower(email))
  password_hash VARCHAR(100)  -- null if Google-only
  google_sub VARCHAR(255)     -- unique when not null
  display_name, locale, base_currency, timezone
  created_at, updated_at
  CHECK (password_hash IS NOT NULL OR google_sub IS NOT NULL)

refresh_tokens
  id UUID PK
  user_id UUID FK → users
  token_hash VARCHAR(64)      -- SHA-256, unique
  expires_at, revoked_at, created_at, user_agent

categories            -- user_id NULL + system_key = system (rent, utilities, installments, other)
  id, user_id?, system_key?, name?, color, icon, created_at, deleted_at (soft delete)

incomes               -- kind FIXED | EXTRA; end_month NULL = indefinite; start = end → one-off
  id, user_id, description, kind, currency, start_month, end_month?
income_amounts        -- amount versions (salary raises)
  id, income_id, effective_month, amount   UNIQUE (income_id, effective_month)

expenses              -- templates: kind ONE_TIME | INSTALLMENTS | RECURRING
  id, user_id, category_id, description, kind, currency, start_month, end_month?, installments?,
  due_day? (NULL = last day), variable_amount, adjust_every_months?, adjust_percent?, adjust_with_inflation, notes
expense_amounts       -- amount versions (rent increases)
  id, expense_id, effective_month, amount   UNIQUE (expense_id, effective_month)

expense_occurrences   -- what is due each month (generated up to the current month)
  id, user_id, expense_id, month, installment_number?, due_date, expected_amount, paid_amount,
  currency, status (PENDING | PARTIAL | PAID), notes   UNIQUE (expense_id, month)
password_reset_tokens -- password recovery: SHA-256 hash only, single use, expires in 30 min
  id, user_id, token_hash UNIQUE, expires_at, used_at?, created_at

inflation_rates       -- official monthly inflation per currency (fraction: 0.019 = 1.9%)
  currency, month, rate, source, fetched_at   PK (currency, month)
user_inflation        -- user's manual inflation per currency (replaces users.monthly_inflation from V3)
  user_id, currency, monthly_rate, updated_at   PK (user_id, currency)

incomes += adjust_every_months?, adjust_percent?, adjust_with_inflation   -- increase rule (V4)
```

## Connecting to the local database

| Field | Value |
|---|---|
| Host | `localhost` |
| Port | `5433` (`DB_PORT` in `.env`) |
| Database | `saver` (and `saver_test` for tests) |
| User / password | `DB_USER` / `DB_PASSWORD` in `.env` |

Works for pgAdmin, DBeaver or IntelliJ. From the terminal:

```bash
docker compose exec postgres psql -U saver -d saver
```

Reset from scratch (deletes data):

```bash
docker compose down -v && docker compose up -d
```

## Integration tests

`mvn test` also runs integration tests against the `docker compose` PostgreSQL (`saver_test` database, created automatically). If the database is not running, those tests are **skipped**. They read the connection from `.env` (`DB_HOST`, `DB_PORT`, `DB_USER`, `DB_PASSWORD`); to target another database set `TEST_DB_HOST`, `TEST_DB_PORT`, `TEST_DB_USER` or `TEST_DB_PASSWORD`.
