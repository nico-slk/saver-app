# Saver — Backend

[🇪🇸 Español](README.md) · 🇬🇧 English

REST API for **Saver**, an app to manage expenses, income and savings and to simulate investments.
Part of the [Saver](../README.en.md) repository; the frontend is in [`../frontend`](../frontend/README.en.md).

| | |
|---|---|
| Language | Java 21 |
| Framework | Spring Boot 3.5 (Web, Security, Data JPA, Validation, Actuator) |
| Authentication | Own JWT (HS256) + rotated refresh token in an HttpOnly cookie + Google Identity |
| Database | PostgreSQL 16 · Flyway migrations |
| Files | S3 API: SeaweedFS in Docker (local, works offline) · production provider to be decided (Supabase Storage, R2, GCS, AWS S3) |
| API docs | OpenAPI 3 · Swagger UI at `/swagger-ui.html` |
| Deployment | Docker · Google Cloud Run |

## Quick start

Requirements: JDK 21, Maven 3.9+, Docker Desktop.

```bash
cp .env.example .env             # Windows PowerShell: Copy-Item .env.example .env
docker compose up -d             # PostgreSQL (:5433) + SeaweedFS (:8333) + Mailpit (:8025)
mvn spring-boot:run              # backend at http://localhost:8080 (or Run from IntelliJ)
```

Docker only runs the infrastructure; the backend runs with Maven or IntelliJ (in IntelliJ, the *working directory* must be this folder so `.env` is loaded). Once the images are pulled, everything works offline.

- Development emails (Mailpit): http://localhost:8025
- Health check: http://localhost:8080/actuator/health
- Swagger UI: http://localhost:8080/swagger-ui.html
- Development receipts live in the `saver_objectdata` Docker volume. On startup in development, the backend creates the bucket if missing.

The backend automatically reads the `.env` file in this folder. Flyway creates and updates the schema on startup.

Stop while retaining data: `docker compose down`.

Remove **only** local media objects: `powershell -ExecutionPolicy Bypass -File scripts/clean-storage.ps1`. The script asks for confirmation and never removes `saver_pgdata`.

## Tests

```bash
mvn test
```

With `docker compose up -d` running, integration tests also run (`saver_test` database); without it, they are skipped.

## Structure

```
src/main/java/com/saver/
├── auth/       # Sign-up, login, Google, refresh and logout
├── user/       # User profile (/api/me), UserTime (current month per time zone)
├── notification/ # Alert preferences by type and channel
├── category/   # System and own categories (soft delete)
├── income/     # Fixed and extra income with versioned amounts
├── expense/    # Expense templates, monthly occurrences, ExpenseSchedule (calculations)
├── month/      # Month view: balance, overdue
├── history/    # Monthly history and month comparison
├── projection/ # N-month projection (nominal and in today's money)
├── inflation/  # Official ARS/USD inflation (daily download), estimate and manual rate
├── exchange/   # Dollar rates (dolarapi) and per-use conversions
├── account/    # Accounts, balances, transfers, adjustments and net worth
├── card/       # Cards (group expenses)
├── payment/    # Occurrence payments and receipts
├── timeline/   # Past + future series for Home
├── mail/       # MailSender port + adapters (log, Mailpit, Brevo)
├── security/   # Spring Security, JWT, @CurrentUser
├── config/     # Typed properties (app.*), clock
├── storage/    # StorageService port + S3 adapter (SeaweedFS, Supabase, R2, AWS…)
└── common/     # Errors (ApiException, ProblemDetail)
src/main/resources/
├── application.yml
└── db/migration/   # Flyway scripts V{n}__description.sql
```

## Documentation

| Topic | Description |
|---|---|
| [Architecture](docs/en/architecture.md) | Layers, packages and technical decisions |
| [Domain](docs/en/domain.md) | Business rules: income, expenses, installments, closings, investments |
| [Configuration](docs/en/configuration.md) | Environment variables and profiles |
| [Authentication](docs/en/authentication.md) | JWT flow, refresh token and Google |
| [API](docs/en/api.md) | Endpoints and error format |
| [Database](docs/en/database.md) | Schema, conventions and migrations |
| [Deployment](docs/en/deployment.md) | Docker, Cloud Run and Supabase |
| [Conventions](docs/en/conventions.md) | Code, commits and branches |

## Roadmap

1. ✅ Foundation: authentication, profile, error i18n, local environment
2. ✅ Income, categories, expenses (one-off, installments, fixed, variable) and monthly balance
3. ✅ (3A) Accounts, cards, dollar rates, partial payments with receipts and income receipts · (3B) versioned automatic month closing, real savings, email reminders
4. ✅ History, month comparisons and projections · ✅ password recovery by email · ✅ official inflation per currency and Home timeline
5. Investment simulator with saved scenarios
6. PDF reports and Excel/CSV export · GCP deployment
