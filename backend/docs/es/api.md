# API

[English](../en/api.md) · [← Volver](../../README.md)

La referencia completa e interactiva está en **Swagger UI**: `http://localhost:8080/swagger-ui.html` (JSON en `/v3/api-docs`).

## Convenciones

- Prefijo `/api`. JSON en UTF-8.
- Autenticación con `Authorization: Bearer <accessToken>`, salvo `/api/auth/**`.
- Montos como string decimal o número con 2 decimales; fechas ISO-8601 (`2026-10-25`, `2026-10-21T20:00:00Z`).
- Los ids son UUID.
- `Accept-Language` se envía pero hoy solo se usa para informes.

## Endpoints (etapa 1: autenticación y perfil)

| Método | Ruta | Auth | Descripción |
|---|---|---|---|
| GET | `/api/auth/providers` | — | Métodos de login habilitados (`googleClientId`) |
| POST | `/api/auth/register` | — | Crea la cuenta. Body: `email`, `password`, `displayName`, `locale` |
| POST | `/api/auth/login` | — | Body: `email`, `password` |
| POST | `/api/auth/google` | — | Body: `idToken`, `locale` |
| POST | `/api/auth/refresh` | cookie | Rota el refresh token y devuelve un access token nuevo |
| POST | `/api/auth/logout` | cookie | Cierra la sesión actual (204) |
| GET | `/api/me` | Bearer | Perfil del usuario |
| PATCH | `/api/me` | Bearer | Actualiza `displayName`, `locale` (`es`/`en`), `baseCurrency` (`ARS`/`USD`), `timezone` |
| GET | `/actuator/health` | — | Estado del servicio |

Respuesta de autenticación:

```json
{
  "accessToken": "eyJhbGciOiJIUzI1NiJ9…",
  "expiresIn": 900,
  "user": {
    "id": "6f1c…",
    "email": "nico@example.com",
    "displayName": "Nico",
    "locale": "es",
    "baseCurrency": "ARS",
    "timezone": "America/Argentina/Buenos_Aires",
    "hasPassword": true,
    "googleLinked": false
  }
}
```

## Endpoints (etapa 2)

| Método | Ruta | Descripción |
|---|---|---|
| GET | `/api/categories?includeDeleted=` | Categorías de sistema + propias. `includeDeleted=true` incluye las borradas (historial) |
| POST | `/api/categories` | Crea. Body: `name`, `color` (`#rrggbb`), `icon?` |
| PATCH | `/api/categories/{id}` | Edita nombre/color. Las de sistema → 403 |
| DELETE | `/api/categories/{id}` | Borrado lógico (204) |
| GET | `/api/incomes` | Ingresos con sus versiones de monto |
| POST | `/api/incomes` | Body: `description`, `kind` (`FIXED`/`EXTRA`), `currency`, `amount`, `startMonth` (`AAAA-MM`), `months?` (1 = puntual, N = se repite N meses, ausente = indefinido) |
| PATCH | `/api/incomes/{id}` | `description?`, `endMonth?`, `clearEnd?` |
| POST | `/api/incomes/{id}/amounts` | Nuevo monto desde un mes: `effectiveMonth`, `amount` |
| DELETE | `/api/incomes/{id}` | Borra (204) |
| GET | `/api/expenses` | Plantillas de gasto (`currentAmount`, `remainingInstallments`, `nextAdjustmentMonth`, `totalAmount`…) |
| POST | `/api/expenses` | Body: `description`, `categoryId`, `kind` (`ONE_TIME`/`INSTALLMENTS`/`RECURRING`), `currency`, `amount` (por mes o por cuota), `startMonth`, `installments?`, `dueDay?` (null = último día), `variableAmount`, `adjustEveryMonths?`, `adjustPercent?`, `adjustWithInflation`, `notes?` |
| PATCH | `/api/expenses/{id}` | `description`, `categoryId`, `dueDay` (0 = último día), `variableAmount`, ajustes, `clearAdjustment`, `endMonth`/`clearEnd` (solo `RECURRING`), `notes` |
| POST | `/api/expenses/{id}/amounts` | Aumento: `effectiveMonth`, `amount`. Recalcula las ocurrencias sin pagos |
| DELETE | `/api/expenses/{id}` | Borra la plantilla y sus ocurrencias (409 si tiene pagos) |
| PATCH | `/api/occurrences/{id}` | Ajuste de un mes: `expectedAmount?`, `dueDate?`, `notes?` |
| GET | `/api/months/{AAAA-MM\|current}` | Vista del mes: `totals` por moneda (`income`, `expenses`, `paid`, `pending`, `overdue`, `balance`), `byCategory`, `incomes`, `occurrences`, `overdue`. Meses futuros: `projected=true` |

Estado de una ocurrencia en la respuesta: `PENDING`, `PARTIAL`, `PAID` u `OVERDUE` (calculado: vencida y no pagada).

## Endpoints (recuperación de contraseña, historial y proyecciones)

| Método | Ruta | Auth | Descripción |
|---|---|---|---|
| POST | `/api/auth/password/forgot` | — | Body: `email`. Siempre 204; si la cuenta existe, envía el email con el link |
| POST | `/api/auth/password/reset` | — | Body: `token`, `password`. 204 y cierra todas las sesiones |
| GET | `/api/history?from=&to=&categories=&currency=` | Bearer | Serie mensual (`months`: `income`, `expenses` filtrado, `expensesTotal`, `savings`, `byCategory`) y `summary`. Por defecto, últimos 12 meses |
| GET | `/api/history/compare?from=&to=&currency=` | Bearer | Compara dos meses: `income`, `expenses`, `savings` (`from`, `to`, `diff`, `percent`), `averageSavings`, `items` por gasto (`status`: `UP`/`DOWN`/`SAME`/`NEW`/`ENDED`) y `categories` |
| GET | `/api/projections?months=&startBalance=&inflationPercent=&currency=` | Bearer | Proyección desde el mes actual: `series` (ingresos, cuotas, fijos, únicos, `balance`, `cumulative`, `cumulativeReal`, `inflationPercent`, `inflationOfficial`, `events`), `inflation` (tasa usada, `source`: `SCREEN`/`MANUAL`/`ESTIMATED`/`NONE`, acumulada) y `summary`. `inflationPercent` cambia la inflación solo para esa simulación |

## Endpoints (inflación y línea de tiempo)

| Método | Ruta | Auth | Descripción |
|---|---|---|---|
| GET | `/api/inflation` | Bearer | Por moneda (ARS, USD): último dato oficial, últimos 12 meses, estimada, manual, tasa proyectada y su origen, últimos 13 meses |
| PUT | `/api/inflation/{moneda}/manual` | Bearer | Body: `monthlyPercent` (0–50). Inflación manual para los meses sin dato oficial |
| DELETE | `/api/inflation/{moneda}/manual` | Bearer | Vuelve a la estimada. 204 |
| GET | `/api/timeline?from=&months=&currency=` | Bearer | Meses desde `from` (por defecto, 6 antes del actual) durante `months` (1–120, por defecto 13): `points` con `type` `PAST`/`CURRENT`/`FUTURE`, `income`, `expenses`, `balance`; `current` y `today` |

`GET /api/history` agrega por mes `inflationPercent`, `inflationOfficial` y `realFactor`, y en `summary` `inflationPercent`, `inflationEstimated`, `totalSavingsReal`, `expensesRealPercent`. `GET /api/history/compare` agrega `inflationPercent`, `inflationEstimated`, `savingsReal`, `expensesRealPercent` y `realPercent` por gasto.
Ingresos (`POST`/`PATCH /api/incomes`) aceptan `adjustEveryMonths`, `adjustPercent`, `adjustWithInflation` (y `clearAdjustment` en `PATCH`); la respuesta incluye `nextAdjustmentMonth`.


## Errores

Formato [RFC 9457](https://www.rfc-editor.org/rfc/rfc9457) (`application/problem+json`) con la propiedad `code`:

```json
{
  "type": "about:blank",
  "title": "Bad Request",
  "status": 400,
  "detail": "validation.failed",
  "code": "validation.failed",
  "fields": { "email": "validation.email", "password": "auth.password_length" }
}
```

| `code` | HTTP | Cuándo |
|---|---|---|
| `validation.failed` | 400 | Body inválido; detalle en `fields` |
| `request.malformed` | 400 | JSON mal formado |
| `auth.invalid_credentials` | 401 | Email o contraseña incorrectos |
| `auth.email_taken` | 409 | El email ya está registrado |
| `auth.password_length` | 400 | Contraseña fuera de 8 caracteres – 72 bytes |
| `auth.unauthorized` | 401 | Falta el access token o es inválido |
| `auth.forbidden` | 403 | Sin permiso |
| `auth.refresh_missing` / `refresh_invalid` / `refresh_expired` | 401 | Problemas con la cookie de refresh |
| `auth.google_disabled` / `google_invalid_token` / `google_email_not_verified` | 400/401 | Login con Google |
| `user.timezone.invalid` | 400 | Zona horaria desconocida |
| `month.invalid` | 400 | Mes con formato distinto de `AAAA-MM` |
| `category.not_found` / `category.name_taken` / `category.system_readonly` / `category.deleted` / `category.color_invalid` | 404/409/403/400 | Categorías |
| `income.not_found` / `income.end_before_start` / `income.amount_before_start` | 404/400 | Ingresos |
| `expense.not_found` / `expense.installments_required` / `expense.adjust_only_recurring` / `expense.end_only_recurring` / `expense.end_before_start` / `expense.amount_before_start` | 404/400 | Gastos |
| `expense.has_payments` | 409 | No se puede borrar un gasto con pagos: darlo de baja |
| `occurrence.not_found` | 404 | Ocurrencia inexistente o de otro usuario |
| `validation.*` (`required`, `too_long`, `positive`, `amount`, `min`, `max`, `currency`) | 400 | Dentro de `fields` |
| `auth.reset_invalid` / `auth.reset_expired` | 400 | Link de recuperación inválido, ya usado o vencido |
| `history.invalid_range` / `history.range_too_long` | 400 | Período inválido o mayor a 120 meses |
| `projection.invalid_months` / `projection.invalid_inflation` | 400 | Horizonte fuera de 1–120 o inflación fuera de 0–50 % |
| `inflation.invalid_currency` | 400 | Moneda distinta de ARS/USD |
| `timeline.invalid_months` / `timeline.invalid_from` | 400 | Cantidad fuera de 1–120 o mes inicial a más de 120 meses |
| `income.adjust_only_recurring` | 400 | Regla de aumentos en un ingreso de un solo mes |
| `server.error` | 500 | Error inesperado (se registra en el log) |

El frontend traduce cada `code` con la clave `errors.<code>`. **Al agregar un código nuevo, sumarlo también en los archivos de idioma del frontend.**
