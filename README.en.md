# Saver

[🇪🇸 Español](README.md) · 🇬🇧 English

> ⚠️ **Important Notice: Demonstration Project**  
> Saver is a low-budget demonstration application (*proof of concept* / MVP) designed for technical evaluation and portfolio showcase. It does not carry enterprise high-availability service level agreements (SLAs) or commercial banking security certifications. **It is strongly advised NOT to enter real financial information, sensitive credentials, or confidential personal data.**

---

Saver is a responsive web application designed to organize personal finances in a single dashboard. It helps users understand money inflows, obligations to cover, and how accounts evolve over time to make everyday decisions with greater financial clarity.

## The Problem It Solves

Income, credit card installments, utility bills, and due dates often end up scattered across paper notes, spreadsheets, calendar reminders, and various banking apps. This makes it difficult to know exactly how much cash is available, which deadlines are approaching, and how an installment purchase will impact upcoming months. Saver centralizes this data into an intuitive monthly view, linking each transaction to concrete accounts and dates.

## Key Features

- **Incomes and Expenses:** Track fixed and extra earnings, salary raises, one-time expenses, recurring subscriptions, and credit card installments.
- **Monthly Overview (Dashboard):** Consolidated balance per currency (ARS/USD), tracking of upcoming deadlines, overdue debts, and category breakdown.
- **Historical Analysis & Inflation Adjustment:** Compare month-over-month variances in income, expenses, and savings in both nominal terms and real purchasing power adjusted by official inflation metrics (INDEC / BLS).
- **Simulator & Financial Projections:** N-month forecast taking into account opening balances, future installments, and customizable or estimated inflation rates.
- **Accounts & Net Worth:** Manage checking accounts, cash, digital wallets, credit cards, reconciliation balance adjustments, and inter-account transfers.
- **Payments & Receipts:** Record full or partial expense payments and income receipts, with currency conversion and digital attachment uploads (cloud object storage).
- **Security & User Management:** Password authentication and optional Google Sign-In, session management using JWT and `HttpOnly` cookies, and email password reset.

---

## Technologies Used

### Frontend
* **React 19** with **TypeScript**: Modern, declarative, type-safe user interface.
* **Vite**: High-performance development server and optimized build tool.
* **Tailwind CSS v4**: Utility-first CSS framework for fast, consistent styling.
* **Zustand**: Lightweight global state management for auth state, alerts, and theme preferences.
* **TanStack Query (React Query)**: Asynchronous data fetching, background synchronization, and caching.
* **React Router v7**: Client-side single page app routing.
* **Recharts**: Interactive data visualizations for timelines, balances, and financial projections.
* **i18next**: Multi-language support (English and Spanish).
* **Axios**: HTTP client with request/response interceptors for automatic session token renewals.

### Backend
* **Java 21 LTS**: Modern Long-Term Support Java runtime.
* **Spring Boot 3.5**:
  * **Spring Web (MVC)**: RESTful API built on standardized controller conventions.
  * **Spring Security**: Endpoint authorization, JWT (`HS256`) verification, and `SameSite=Strict` session cookies.
  * **Spring Data JPA & Hibernate**: Object-relational mapping and repository abstraction layer.
* **PostgreSQL 16**: Relational database engine ensuring ACID compliance and referential integrity.
* **Flyway**: Database schema versioning and automated migrations.
* **AWS SDK v2 for Java**: S3-compatible client for storing payment receipts and file attachments.
* **Spring Boot Actuator**: Health checks and system operational metrics (`/actuator/health`).

### Cloud Infrastructure & Managed Services
* **Google Cloud Run (`southamerica-east1` - São Paulo):** Serverless Docker container hosting for the backend API, auto-scaling from 0 to 2 instances to minimize cost.
* **Firebase Hosting:** Global CDN serving static frontend assets, with URL rewrite rules (`/api/**`) forwarding requests directly to Cloud Run on the same origin (enabling secure cookies without third-party cookie restrictions).
* **Supabase:**
  * **Managed PostgreSQL**: Connected via the Supavisor *Session Pooler* (port 5432).
  * **Supabase Storage**: Private S3-compliant bucket for receipt file storage.
* **Brevo (formerly Sendinblue):** Transactional email delivery service for password reset emails.
* **Google Secret Manager:** Secure storage of production credentials (database passwords, API keys, JWT signing keys, and S3 access tokens).
* **Google Artifact Registry & Cloud Build:** Container image repository and cloud build pipeline.

---

## Deployment Workflow and Branching Strategy (CI/CD)

The project is structured as a **monorepo** containing both `frontend` and `backend`. Delivery is automated through **GitHub Actions**.

### Git Branching Model
* **`main` (Production):**
  * Represents code currently live in production.
  * Any `push` or `merge` to this branch triggers the automated CI/CD deployment pipelines.
  * **`hotfix/*`** branches are cut directly from `main`, patched, merged back into `main` (triggering an immediate release), and then back-merged into `develop`.
* **`develop` (Development):**
  * Integration branch for day-to-day work.
  * New features (**`feature/*`**) and normal bug fixes (**`fix/*`**) branch from and merge back into `develop`.
  * Releases are scheduled by opening a Pull Request from `develop` into `main`.

### GitHub Actions Workflows
1. **Backend Deployment ([`deploy-backend.yml`](.github/workflows/deploy-backend.yml)):**
   * Triggered on changes inside `backend/**` pushed to `main`.
   * Authenticates with Google Cloud using a dedicated Service Account (`GCP_SA_KEY`).
   * Builds the Docker image via Google Cloud Build, pushes to Artifact Registry, and deploys a new Cloud Run revision with production secrets injected.
2. **Frontend Deployment ([`deploy-frontend.yml`](.github/workflows/deploy-frontend.yml)):**
   * Triggered on changes inside `frontend/**` pushed to `main`.
   * Installs Node.js dependencies, builds the production bundle with Vite (`dist/`), and deploys directly to Firebase Hosting.

---

## Repository Structure

| Folder | Description |
|---|---|
| [`backend/`](backend/README.en.md) | REST API source code, Flyway migrations, Dockerfile, and integration tests. |
| [`frontend/`](frontend/README.en.md) | React SPA source code with Vite, Tailwind CSS, and UI components. |
| [`docs/`](docs/README.md) | Technical architecture, cloud strategy, and [API reference documentation](docs/api_reference.md). |
| [`.github/workflows/`](.github/workflows/) | GitHub Actions CI/CD pipeline definitions. |
| `saver_postman_collection.json` | Postman v2.1.0 collection to test and inspect all API endpoints. |

---

## Local Development Setup

### Prerequisites
* JDK 21
* Maven 3.9+
* Node.js 20+
* Docker Desktop

```bash
# 1. Setup local environment file
cp backend/.env.example backend/.env

# 2. Start local database and object storage
cd backend
docker compose up -d

# 3. Start Spring Boot API
mvn spring-boot:run

# 4. In another terminal, start the Frontend
cd ../frontend
npm install
npm run dev
```

* **Frontend:** `http://localhost:5173`
* **Backend API:** `http://localhost:8080` (Swagger UI: `http://localhost:8080/swagger-ui.html`)
* **Local Docker Infrastructure:** PostgreSQL (`:5433`), SeaweedFS S3 (`:8333`), Mailpit (`:8025`).
