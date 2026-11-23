# Base de datos

[English](../en/database.md) · [← Volver](../../README.md)

PostgreSQL 16. Local en Docker; en producción, Supabase (o Cloud SQL).

## Convenciones

| Elemento | Regla |
|---|---|
| Tablas y columnas | `snake_case`, tablas en plural (`users`, `refresh_tokens`) |
| Clave primaria | `id UUID`, generado en Java |
| Pertenencia | Toda tabla de datos del usuario tiene `user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE` + índice |
| Fechas y horas | `TIMESTAMPTZ` (UTC) |
| Fechas sin hora (vencimientos, meses) | `DATE`; un mes se representa como `DATE` del día 1 |
| Montos | `NUMERIC(15,2)` · cotizaciones `NUMERIC(15,4)` |
| Monedas | `VARCHAR(3)` con `CHECK` (`ARS`, `USD`) |
| Enums | `VARCHAR` + `CHECK`, mapeados con `@Enumerated(EnumType.STRING)` |
| Textos | `VARCHAR(n)` (Hibernate valida `varchar`, no `text`) |
| Borrado lógico | `deleted_at TIMESTAMPTZ NULL` donde el historial lo requiera (ej. categorías) |
| Auditoría | `created_at`, `updated_at` |

## Migraciones (Flyway)

- Ubicación: `src/main/resources/db/migration`.
- Nombre: `V{n}__descripcion_en_snake_case.sql` (ej. `V2__categories_and_incomes.sql`).
- **Nunca modificar una migración ya aplicada**: crear una nueva.
- Hibernate corre con `ddl-auto: validate`: si una entidad no coincide con el esquema, la aplicación no arranca.

## Esquema actual

```
users
  id UUID PK
  email VARCHAR(320)          -- único (lower(email))
  password_hash VARCHAR(100)  -- null si solo usa Google
  google_sub VARCHAR(255)     -- único si no es null
  display_name, locale, base_currency, timezone
  created_at, updated_at
  CHECK (password_hash IS NOT NULL OR google_sub IS NOT NULL)

refresh_tokens
  id UUID PK
  user_id UUID FK → users
  token_hash VARCHAR(64)      -- SHA-256, único
  expires_at, revoked_at, created_at, user_agent

categories            -- user_id NULL + system_key = de sistema (rent, utilities, installments, other)
  id, user_id?, system_key?, name?, color, icon, created_at, deleted_at (borrado lógico)

incomes               -- kind FIXED | EXTRA; end_month NULL = indefinido; start = end → puntual
  id, user_id, description, kind, currency, start_month, end_month?
income_amounts        -- versiones de monto (aumentos de sueldo)
  id, income_id, effective_month, amount   UNIQUE (income_id, effective_month)

expenses              -- plantillas: kind ONE_TIME | INSTALLMENTS | RECURRING
  id, user_id, category_id, description, kind, currency, start_month, end_month?, installments?,
  due_day? (NULL = último día), variable_amount, adjust_every_months?, adjust_percent?, adjust_with_inflation, notes
expense_amounts       -- versiones de monto (aumentos del alquiler)
  id, expense_id, effective_month, amount   UNIQUE (expense_id, effective_month)

expense_occurrences   -- lo que corresponde pagar cada mes (generado hasta el mes actual)
  id, user_id, expense_id, month, installment_number?, due_date, expected_amount, paid_amount,
  currency, status (PENDING | PARTIAL | PAID), notes   UNIQUE (expense_id, month)
password_reset_tokens -- recuperación de contraseña: solo el hash SHA-256, un solo uso, vence a los 30 min
  id, user_id, token_hash UNIQUE, expires_at, used_at?, created_at

inflation_rates       -- inflación oficial mensual por moneda (fracción: 0.019 = 1,9 %)
  currency, month, rate, source, fetched_at   PK (currency, month)
user_inflation        -- inflación manual del usuario por moneda (reemplaza a users.monthly_inflation de V3)
  user_id, currency, monthly_rate, updated_at   PK (user_id, currency)

incomes += adjust_every_months?, adjust_percent?, adjust_with_inflation   -- regla de aumentos (V4)
accounts              -- cuentas (V5): kind CHECKING | CASH | WALLET | SAVINGS | INVESTMENT
  id, user_id, name, kind, currency, opening_balance, opening_date, color, archived_at
cards                 -- tarjetas (solo agrupan gastos); expenses.card_id → cards
  id, user_id, name, color, archived_at
exchange_rates        -- cotizaciones por día y tipo (pesos por dólar)   PK (day, type)
  day, type (OFICIAL | MEP | BLUE | TARJETA | CCL | MAYORISTA | CRIPTO), buy?, sell, source, fetched_at
payments              -- pagos de ocurrencias; assumed = meses pasados (sin cuenta)
  id, user_id, occurrence_id, account_id?, paid_on, amount, account_amount?, rate?, rate_type?, notes, assumed
payment_attachments   -- comprobantes (el archivo está en el storage)
  id, user_id, payment_id, storage_key, file_name, content_type, size_bytes
income_receipts       -- cobros de ingresos por mes
  id, user_id, income_id, month, account_id, received_on, amount, account_amount, rate?, rate_type?, notes
transfers             -- entre cuentas propias (compra/venta de dólares si cambia la moneda)
  id, user_id, from_account_id, to_account_id, transfer_on, from_amount, to_amount, rate?, rate_type?, notes
account_adjustments   -- ajustes de saldo con signo
  id, user_id, account_id, adjusted_on, amount, notes
```

## Conectarse a la base local

| Dato | Valor |
|---|---|
| Host | `localhost` |
| Puerto | `5433` (el `DB_PORT` del `.env`) |
| Base | `saver` (y `saver_test` para los tests) |
| Usuario / contraseña | `DB_USER` / `DB_PASSWORD` del `.env` |

Sirve para pgAdmin, DBeaver o IntelliJ. Desde la terminal:

```bash
docker compose exec postgres psql -U saver -d saver
```

Reiniciar desde cero (borra los datos):

```bash
docker compose down -v && docker compose up -d
```

## Tests de integración

`mvn test` ejecuta también tests de integración contra el PostgreSQL de `docker compose` (base `saver_test`, se crea sola). Si la base no está levantada, esos tests se **omiten**. Toman la conexión del `.env` (`DB_HOST`, `DB_PORT`, `DB_USER`, `DB_PASSWORD`); para usar otra base, definir `TEST_DB_HOST`, `TEST_DB_PORT`, `TEST_DB_USER` o `TEST_DB_PASSWORD`.
