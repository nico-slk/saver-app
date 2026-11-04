# Arquitectura

[English](../en/architecture.md) · [← Volver](../../README.md)

## Principios

- **Organización por funcionalidad**: cada dominio (`auth`, y luego `expenses`, `incomes`, `history`…) vive en `src/features/<nombre>` con sus páginas, componentes y cliente de API.
- **Componentes de UI propios y livianos** en `src/components/ui`, sin librerías de componentes.
- **Alias `@/`** apunta a `src/` (configurado en `vite.config.ts` y `tsconfig.app.json`).
- **TypeScript estricto** (`strict`, `noUncheckedIndexedAccess`, `noUnusedLocals`).

## Rutas

| Ruta | Página | Acceso |
|---|---|---|
| `/login` | Inicio de sesión | Público (redirige a `/` si ya hay sesión) |
| `/register` | Registro | Público |
| `/forgot-password` | Pedir link para recuperar la contraseña | Público |
| `/reset-password?token=` | Elegir contraseña nueva (link del email) | Público |
| `/?from=AAAA-MM&months=N` | Inicio (dashboard). `from`/`months`: rango del gráfico pasado + proyección (por defecto 6 meses antes y 6 después) | Protegido |
| `/expenses?tab=month\|templates\|categories&month=AAAA-MM` | Gastos del mes, fijos y cuotas, categorías | Protegido |
| `/incomes` | Ingresos | Protegido |
| `/history?from=&to=&cat=&real=1` | Historial: período, filtro de categorías, valores ajustados por inflación y comparación | Protegido |
| `/projections` | Proyecciones (horizonte y saldo inicial se recuerdan en el dispositivo; la inflación, en el perfil) | Protegido |
| `/settings` | Ajustes (perfil, inflación por moneda, tema) | Protegido |
| `*` | 404 | Protegido |

Las rutas protegidas cuelgan de `ProtectedLayout`. Mientras se recupera la sesión muestra un loader; si no hay sesión, redirige a `/login` y recuerda la página pedida para volver después del login.

## Sesión

```
Carga de la app
  └─▶ authStore.bootstrap() → POST /api/auth/refresh (cookie HttpOnly)
        ├─ ok    → guarda accessToken + user en memoria → status "authenticated"
        └─ error → status "anonymous" → /login

Request con 401
  └─▶ interceptor de axios → refresh (uno solo, compartido) → reintenta la request
        └─ si el refresh falla → se limpia la sesión → /login
```

- El **access token vive solo en memoria** (nunca en `localStorage`), así un script inyectado no puede robar una sesión duradera.
- El **refresh token** está en una cookie `HttpOnly` que el navegador envía solo a `/api/auth/*`.
- Los refresh se **serializan entre pestañas** con la Web Locks API: el backend rota el token en cada uso y detecta reutilizaciones.
- A `/api/auth/*` nunca se le envía el header `Authorization`.

## Manejo de errores

El backend responde `ProblemDetail` con un `code` (`auth.invalid_credentials`). `lib/errors.ts` lo extrae y `translateError(t, err)` lo traduce con la clave `errors.<code>`. Los errores de validación por campo llegan en `fields`.

## Layout responsive

| Ancho | Navegación |
|---|---|
| `< 1024px` | Header superior (idioma, ajustes, salir) + barra inferior: Inicio, Gastos, Ingresos, Historial, Proyecciones |
| `≥ 1024px` | Menú lateral fijo con todas las secciones |

Las secciones de etapas futuras aparecen deshabilitadas con la etiqueta “Pronto”. Las páginas se cargan de forma diferida (`React.lazy`): Recharts solo se descarga al entrar a una página con gráficos (Ajustes también es diferida porque muestra la inflación).

## Datos financieros

- `GET /api/months/{mes}` es la fuente del inicio y de «Gastos del mes»; el mes elegido vive en la URL (`?month=`).
- Tras crear, editar o borrar ingresos, gastos o categorías, `useFinanceInvalidation()` refresca todas las consultas relacionadas.
- Los montos se envían como string (`"15000.50"`) para no perder precisión.
