#!/bin/bash
# Levanta el entorno de desarrollo SIN el backend: Docker (Postgres, SeaweedFS, Mailpit) y el frontend.
# El backend se levanta aparte (IntelliJ o `mvn spring-boot:run` en la carpeta backend; lee backend/.env).
# Uso (desde la raíz del repo, en Git Bash): ./run.sh   — Ctrl+C detiene el frontend (los contenedores siguen).

# Terminar todo el script si algún comando falla
set -e

ROOT="$(cd "$(dirname "$0")" && pwd)"

echo "🚀 Iniciando el entorno de desarrollo (sin backend)..."

# 1. Infraestructura: Postgres (DB_PORT del backend/.env, 5433 por defecto), SeaweedFS (8333) y Mailpit (8025)
echo "🐳 Iniciando contenedores de Docker (base de datos, almacenamiento y emails)..."
cd "$ROOT/backend"
docker compose up -d

echo "☕ Backend: levantalo vos (IntelliJ o 'mvn spring-boot:run' en backend/). Debe quedar en http://localhost:8080"

# 2. Frontend con React y Vite (el proxy /api apunta a http://localhost:8080 y reintenta hasta que el backend responda)
echo "⚡ Iniciando frontend con Vite..."
cd "$ROOT/frontend"
npm install
npm run dev
