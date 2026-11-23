# Saver — Frontend

🇪🇸 Español · [🇬🇧 English](README.en.md)

Aplicación web responsive de **Saver** para gestionar gastos, ingresos, ahorros y simular inversiones.
Forma parte del repositorio [Saver](../README.md). La API está en [`../backend`](../backend/README.md), que también documenta las [reglas de negocio](../backend/docs/es/dominio.md).

| | |
|---|---|
| UI | React 19 · TypeScript · Vite |
| Estilos | Tailwind CSS 4 (modo claro/oscuro) |
| Estado | Zustand (sesión y preferencias) · TanStack Query (datos del servidor) |
| HTTP | Axios con renovación automática de sesión |
| Idiomas | i18next / react-i18next — español e inglés, con detección automática |
| Gráficos | Recharts |
| Tests | Vitest |
| Despliegue | Firebase Hosting |

## Inicio rápido

Requisitos: Node 20+ y el backend corriendo en `http://localhost:8080`.

```bash
npm install
npm run dev        # http://localhost:5173
```

En desarrollo, Vite redirige `/api` al backend (mismo origen, sin CORS).

## Scripts

| Comando | Descripción |
|---|---|
| `npm run dev` | Servidor de desarrollo |
| `npm run build` | Chequeo de tipos + build de producción en `dist/` |
| `npm run typecheck` | Solo chequeo de tipos |
| `npm test` | Tests con Vitest |
| `npm run preview` | Sirve el build localmente |

## Estructura

```
src/
├── components/
│   ├── layout/    # AppLayout (menú lateral / barra inferior), LanguageSwitcher
│   ├── ui/        # Button, Input, Select, Card, Alert, Spinner, Modal, Tabs, Badge
│   ├── charts/    # Tema y tooltip de gráficos (Recharts)
│   └── finance/   # Diálogos de aumento/baja, regla de aumentos, indicadores
├── features/
│   ├── auth/        # Login, registro, Google, recuperar contraseña
│   ├── accounts/    # Cuentas, patrimonio, transferencias, ajustes, tarjetas, cotizaciones
│   ├── payments/    # Pagar (pagos parciales, cuenta, cotización, comprobantes)
│   ├── categories/  # Categorías (useCategories, panel)
│   ├── incomes/     # Página y formulario de ingresos
│   ├── expenses/    # Gastos del mes, plantillas, formulario, vencimientos
│   ├── history/     # Historial, filtros, valores reales y comparación de meses
│   ├── inflation/   # Inflación por moneda (panel de Ajustes)
│   ├── timeline/    # Gráfico pasado + proyección del inicio
│   ├── projections/ # Proyección a N meses
│   └── months/      # Vista del mes, resumen, gráficos
├── i18n/          # Configuración y archivos de idioma (es.json, en.json)
├── lib/           # api (axios), errors, format (moneda/fechas), month (AAAA-MM)
├── pages/         # Dashboard, Ajustes
├── stores/        # authStore, uiStore (Zustand)
├── App.tsx        # Rutas
└── main.tsx       # Providers
```

## Documentación

| Tema | Descripción |
|---|---|
| [Arquitectura](docs/es/arquitectura.md) | Estructura, rutas y flujo de sesión |
| [Estado y datos](docs/es/estado-y-datos.md) | Cuándo usar Zustand y cuándo TanStack Query |
| [Internacionalización](docs/es/i18n.md) | Idiomas, detección y cómo agregar textos |
| [Estilos](docs/es/estilos.md) | Tailwind, tema, responsive e impresión |
| [Despliegue](docs/es/despliegue.md) | Firebase Hosting y conexión con la API |
| [Convenciones](docs/es/convenciones.md) | Código, commits y ramas |
