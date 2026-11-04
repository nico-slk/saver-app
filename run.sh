#!/bin/bash
# Levanta todo el entorno de desarrollo: Docker (Postgres + SeaweedFS), backend y frontend.
# Uso (desde la raíz del repo, en Git Bash): ./run.sh   — Ctrl+C detiene backend y frontend.

# Terminar todo el script si algún comando falla
set -e

ROOT="$(cd "$(dirname "$0")" && pwd)"
BACKEND_PID=""

# Al salir (Ctrl+C o error) se detiene también Spring Boot.
# Se registra ANTES de lanzar los procesos para que siempre se ejecute.
cleanup() {
  if [ -n "$BACKEND_PID" ] && kill -0 "$BACKEND_PID" 2>/dev/null; then
    echo "🛑 Deteniendo Spring Boot..."
    kill "$BACKEND_PID" 2>/dev/null || true
  fi
}
trap cleanup EXIT INT TERM

echo "🚀 Iniciando el entorno de desarrollo..."

# 1. Infraestructura: Postgres (puerto DB_PORT del backend/.env, 5433 por defecto) y SeaweedFS (8333)
echo "🐳 Iniciando contenedores de Docker (base de datos y almacenamiento)..."
cd "$ROOT/backend"
docker compose up -d

# 2. Backend con Spring Boot (en segundo plano; lee backend/.env)
echo "☕ Iniciando backend con Spring Boot..."
mvn spring-boot:run &
BACKEND_PID=$!

# 3. Frontend con React y Vite (Vite reintenta el proxy /api mientras el backend termina de arrancar)
echo "⚡ Iniciando frontend con Vite..."
cd "$ROOT/frontend"
npm install
npm run dev
