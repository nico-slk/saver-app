# Saver

[🇪🇸 Español](README.md) · 🇬🇧 English

Saver is a responsive web app for organizing personal finances in one place. It helps people understand what money comes in, which expenses need to be covered, and how their accounts may evolve so they can make everyday decisions with more clarity.

## The problem it solves

Income, installments, bills, and due dates are often scattered across notes, spreadsheets, reminders, and banking apps. That makes it hard to know what is available, which payments are coming up, and how an expense affects later months. Saver brings this information into a monthly view and connects movements to accounts and dates.

## What it offers today

- Record fixed income, one-off or recurring extras, and raises; track one-time, recurring, and installment expenses.
- Review monthly balances, due dates, pending and overdue bills, and expenses by category.
- Compare months and explore changes in income, spending, and savings, including inflation-adjusted values.
- Project income, installments, expenses, and inflation across a chosen period in nominal or today's money.
- Manage ARS and USD accounts, cards, transfers, balance adjustments, and net worth.
- Record partial payments and income receipts, including exchange rates and supporting documents when relevant.
- Set language, currency, time zone, light/dark theme, and notification preferences.
- Use password or optional Google sign-in, protected sessions, and email password recovery.

## Potential benefits and impact

By bringing financial information together, Saver can reduce missed payments and the mental load of remembering each bill; make demanding months visible earlier; help identify expenses growing faster than inflation; and support decisions about spending, saving, and budget adjustments. As a person records more movements, projections can help anticipate commitments and compare scenarios.

The impact depends on how consistently and accurately the data is entered. Saver provides organization and estimates to support decisions; it does not replace personal judgment or constitute financial advice.

## Current status

Operational capabilities are listed above. Notification preferences can be saved per account, including desired alert types and email or browser channels; automatic delivery through those channels is still being prepared. The menu may preview future capabilities such as month closing, budgets, bank imports, investment simulations, and downloadable reports. They are not available until their rules, screens, and services are operational.

## Technology and layout

| Folder | Contents |
|---|---|
| [`backend/`](backend/README.en.md) | REST API — Java 21, Spring Boot, PostgreSQL, and Flyway |
| [`frontend/`](frontend/README.en.md) | SPA — React, TypeScript, Vite, Tailwind CSS, and Zustand |
| [`docs/`](docs/README.md) | Product, architecture, and deployment documentation |
| `run.sh` | Starts local infrastructure and the frontend; run the backend separately |

The app separates its UI, API, database, and object storage so a cloud provider can be chosen later based on cost, region, free-tier limits, backups, and expected volume. No final provider is assumed.

## Quick start

Requirements: JDK 21, Maven 3.9+, Node 20+, and Docker Desktop.

```bash
cp backend/.env.example backend/.env    # first run only
./run.sh                                # Git Bash: Docker + frontend (run backend separately)
```

Or separately:

```bash
cd backend && docker compose up -d && mvn spring-boot:run   # API at http://localhost:8080
cd frontend && npm install && npm run dev                   # App at http://localhost:5173
```

| Service | Local port |
|---|---|
| Frontend (Vite) | 5173 |
| Backend (Spring Boot) | 8080 |
| PostgreSQL (Docker) | 5433 |
| SeaweedFS, S3 API (Docker) | 8333 |
| Mailpit, development email | 8025 |

`.env` files contain local configuration and should stay out of the repository; use `.env.example` as a reference.
