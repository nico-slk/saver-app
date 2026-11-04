# Saver

🇪🇸 Español · [🇬🇧 English](README.en.md)

Aplicación web responsive para gestionar gastos, ingresos y ahorros, y proyectar inversiones.

Este repositorio contiene todo el proyecto:

| Carpeta | Contenido |
|---|---|
| [`backend/`](backend/README.md) | API REST — Java 21, Spring Boot 3.5, PostgreSQL, Flyway. Incluye el `docker-compose.yml` con la infraestructura local |
| [`frontend/`](frontend/README.md) | SPA — React 19, TypeScript, Vite, Tailwind CSS, Zustand |
| [`docs/`](docs/README.md) | Documentación general: negocio, técnica y despliegue |
| `run.sh` | Levanta todo el entorno de desarrollo con un solo comando |

## Inicio rápido

Requisitos: JDK 21, Maven 3.9+, Node 20+ y Docker Desktop.

```bash
cp backend/.env.example backend/.env    # solo la primera vez
./run.sh                                # Git Bash: Docker + backend + frontend
```

O por separado:

```bash
cd backend && docker compose up -d && mvn spring-boot:run   # API en http://localhost:8080
cd frontend && npm install && npm run dev                   # App en http://localhost:5173
```

| Servicio | Puerto local |
|---|---|
| Frontend (Vite) | 5173 |
| Backend (Spring Boot) | 8080 |
| PostgreSQL (Docker) | 5433 (`DB_PORT` en `backend/.env`; el 5432 queda libre para un Postgres instalado) |
| SeaweedFS, API S3 (Docker) | 8333 |
| Mailpit, emails de desarrollo (Docker) | 8025 (bandeja web) |

## Estructura del repositorio

- El `docker-compose.yml` vive en `backend/` porque es infraestructura del backend y usa el mismo `backend/.env` que Spring Boot (`DB_PORT`, credenciales, bucket). Así hay una sola fuente de configuración.
- Un único `.gitignore` y `.gitattributes` en la raíz cubren las tres carpetas.
- Nunca se versionan los `.env` (solo los `.env.example`).

## Commits

[Conventional Commits](https://www.conventionalcommits.org/es/) indicando el área: `feat(backend): …`, `fix(frontend): …`, `docs: …`.
