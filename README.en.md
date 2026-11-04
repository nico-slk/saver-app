# Saver

[🇪🇸 Español](README.md) · 🇬🇧 English

Responsive web app to manage expenses, income and savings, and to project investments.

This repository contains the whole project:

| Folder | Content |
|---|---|
| [`backend/`](backend/README.en.md) | REST API — Java 21, Spring Boot 3.5, PostgreSQL, Flyway. Includes the `docker-compose.yml` with local infrastructure |
| [`frontend/`](frontend/README.en.md) | SPA — React 19, TypeScript, Vite, Tailwind CSS, Zustand |
| [`docs/`](docs/README.md) | General documentation: business, technical and deployment |
| `run.sh` | Starts the whole development environment with one command |

## Quick start

Requirements: JDK 21, Maven 3.9+, Node 20+ and Docker Desktop.

```bash
cp backend/.env.example backend/.env    # first time only
./run.sh                                # Git Bash: Docker + backend + frontend
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
| PostgreSQL (Docker) | 5433 (`DB_PORT` in `backend/.env`; 5432 stays free for an installed Postgres) |
| SeaweedFS, S3 API (Docker) | 8333 |
| Mailpit, development emails (Docker) | 8025 (web inbox) |

## Repository layout

- `docker-compose.yml` lives in `backend/` because it is backend infrastructure and uses the same `backend/.env` as Spring Boot (`DB_PORT`, credentials, bucket): a single source of configuration.
- A single root `.gitignore` and `.gitattributes` cover all three folders.
- `.env` files are never committed (only `.env.example`).

## Commits

[Conventional Commits](https://www.conventionalcommits.org/) naming the area: `feat(backend): …`, `fix(frontend): …`, `docs: …`.
