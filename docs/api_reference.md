# Referencia Completa de la API de Saver

Documentación exhaustiva de todos los endpoints, métodos HTTP, parámetros de ruta, queries, cuerpos de solicitud (`body`), respuestas y códigos de error de la API de Saver (Spring Boot 3).

---

## 1. Convenciones Generales

### URLs Base
* **Producción (Cloud Run directo):** `https://saver-backend-5585932474.southamerica-east1.run.app`
* **Producción (Firebase Hosting / Mismo origen):** `https://saverapp-b82fd.web.app` (reenvía `/api/**` a Cloud Run)
* **Local (Docker / Maven):** `http://localhost:8080`

### Autenticación y Seguridad
* **Access Token:** JWT firmado con HMAC-SHA256 (`HS256`). Se envía en el header HTTP:
  ```http
  Authorization: Bearer <accessToken>
  ```
* **Refresh Token:** Cadena opaca segura de 32 bytes guardada en cookie HttpOnly:
  * Nombre de la cookie: `__session` (requerido para compatibilidad con Firebase Hosting).
  * Atributos: `HttpOnly`, `Path=/api/auth`, `SameSite=Strict`, `Secure` (en producción).
* **Formatos de datos:**
  * Fechas: `AAAA-MM-DD` (ej. `2026-11-22`).
  * Meses: `AAAA-MM` (ej. `2026-11`) o el valor reservado `current`.
  * Montos: Decimales con hasta 2 cifras fraccionarias (`150000.50`).
  * Identificadores: Formato UUID estándar v4 (`3fa85f64-5717-4562-b3fc-2c963f66afa6`).

### Estructura de Errores (RFC 9457)
Todas las respuestas de error retornan `Content-Type: application/problem+json` con la propiedad de negocio `code`:
```json
{
  "type": "about:blank",
  "title": "Bad Request",
  "status": 400,
  "detail": "validation.failed",
  "code": "validation.failed",
  "fields": {
    "email": "validation.email",
    "password": "auth.password_length"
  }
}
```

---

## 2. Colección de Endpoints

### 01. Autenticación (`/api/auth`)

#### `GET /api/auth/providers`
* **Auth:** Pública.
* **Descripción:** Lista los métodos de autenticación habilitados (permite al frontend saber si debe mostrar el botón de Google).
* **Respuesta (200 OK):**
  ```json
  {
    "password": true,
    "googleClientId": "5585932474-xxx.apps.googleusercontent.com"
  }
  ```

#### `POST /api/auth/register`
* **Auth:** Pública.
* **Descripción:** Registra un nuevo usuario, crea una sesión y setea la cookie `__session`.
* **Body:**
  ```json
  {
    "email": "usuario@ejemplo.com",
    "password": "Password123!",
    "displayName": "Nicolas Selicki",
    "locale": "es"
  }
  ```
* **Respuesta (201 Created):**
  ```json
  {
    "accessToken": "eyJhbGciOi...",
    "expiresIn": 900,
    "user": {
      "id": "762c94ad-2374-4dc4-839e-e81c7ff4794e",
      "email": "usuario@ejemplo.com",
      "displayName": "Nicolas Selicki",
      "locale": "es",
      "baseCurrency": "ARS",
      "timezone": "America/Argentina/Buenos_Aires",
      "hasPassword": true,
      "googleLinked": false
    }
  }
  ```

#### `POST /api/auth/login`
* **Auth:** Pública.
* **Descripción:** Inicia sesión con email y contraseña.
* **Body:**
  ```json
  {
    "email": "usuario@ejemplo.com",
    "password": "Password123!"
  }
  ```
* **Respuesta (200 OK):** Mismo formato que `/api/auth/register`. Setea cookie `__session`.

#### `POST /api/auth/google`
* **Auth:** Pública.
* **Descripción:** Inicia sesión o vincula cuenta mediante el ID Token emitido por Google Identity Services.
* **Body:**
  ```json
  {
    "idToken": "eyJhbGciOi...",
    "locale": "es"
  }
  ```
* **Respuesta (200 OK):** Mismo formato que `/api/auth/register`.

#### `POST /api/auth/refresh`
* **Auth:** Requiere cookie `__session`.
* **Descripción:** Rota el refresh token y devuelve un nuevo `accessToken`.
* **Respuesta (200 OK):** Objeto `AuthResponse`.

#### `POST /api/auth/logout`
* **Auth:** Requiere cookie `__session`.
* **Descripción:** Revoca el refresh token actual y borra la cookie en el navegador.
* **Respuesta:** `204 No Content`.

#### `POST /api/auth/password/forgot`
* **Auth:** Pública.
* **Descripción:** Solicita correo de restablecimiento de contraseña. Siempre responde 204 para evitar enumeración de usuarios.
* **Body:**
  ```json
  {
    "email": "usuario@ejemplo.com"
  }
  ```
* **Respuesta:** `204 No Content`.

#### `POST /api/auth/password/reset`
* **Auth:** Pública.
* **Descripción:** Aplica nueva contraseña con el token recibido por correo electrónico y revoca todas las sesiones activas.
* **Body:**
  ```json
  {
    "token": "4e1837a7-5cc0-4965-b103-9b88c7d6d34b",
    "password": "NuevaPassword123!"
  }
  ```
* **Respuesta:** `204 No Content`.

---

### 02. Perfil y Preferencias (`/api/me`, `/api/notifications`)

#### `GET /api/me`
* **Auth:** Bearer Token.
* **Descripción:** Retorna los datos de perfil y configuración del usuario autenticado.
* **Respuesta (200 OK):**
  ```json
  {
    "id": "762c94ad-2374-4dc4-839e-e81c7ff4794e",
    "email": "usuario@ejemplo.com",
    "displayName": "Nicolas",
    "locale": "es",
    "baseCurrency": "ARS",
    "timezone": "America/Argentina/Buenos_Aires",
    "hasPassword": true,
    "googleLinked": false
  }
  ```

#### `PATCH /api/me`
* **Auth:** Bearer Token.
* **Descripción:** Actualiza campos del perfil. Todos los campos son opcionales.
* **Body:**
  ```json
  {
    "displayName": "Nicolas S.",
    "locale": "es",
    "baseCurrency": "ARS",
    "timezone": "America/Argentina/Buenos_Aires"
  }
  ```
* **Respuesta (200 OK):** Objeto `UserResponse` actualizado.

#### `GET /api/notifications/preferences`
* **Auth:** Bearer Token.
* **Descripción:** Obtiene las preferencias de alertas de vencimiento (próximos, del día, vencidos).
* **Respuesta (200 OK):**
  ```json
  {
    "items": [
      { "kind": "DUE_SOON", "enabled": true, "emailEnabled": true, "pushEnabled": false },
      { "kind": "DUE_TODAY", "enabled": true, "emailEnabled": true, "pushEnabled": false },
      { "kind": "OVERDUE", "enabled": true, "emailEnabled": true, "pushEnabled": false }
    ]
  }
  ```

#### `PUT /api/notifications/preferences`
* **Auth:** Bearer Token.
* **Descripción:** Actualiza las preferencias de notificación.
* **Body:** Mismo formato que la respuesta anterior.
* **Respuesta (200 OK):** Objeto `PreferencesResponse`.

---

### 03. Cuentas y Transferencias (`/api/accounts`, `/api/transfers`, `/api/adjustments`)

#### `GET /api/accounts`
* **Auth:** Bearer Token.
* **Descripción:** Lista todas las cuentas activas y archivadas con su saldo calculado, totales por moneda y patrimonio neto (`netWorth`).
* **Respuesta (200 OK):**
  ```json
  {
    "accounts": [
      {
        "id": "1d8eb697-75e1-4541-bce0-71775a6109df",
        "name": "Banco Galicia",
        "kind": "CHECKING",
        "currency": "ARS",
        "color": "#2563eb",
        "openingBalance": 150000.00,
        "openingDate": "2026-11-01",
        "balance": 230500.00,
        "savings": false,
        "archived": false
      }
    ],
    "totals": [
      { "currency": "ARS", "balance": 230500.00, "savings": 50000.00 },
      { "currency": "USD", "balance": 1200.00, "savings": 1200.00 }
    ],
    "baseCurrency": "ARS",
    "netWorth": 1670500.00,
    "netWorthSavings": 1490000.00,
    "valuationRate": 1200.00,
    "valuationType": "MEP"
  }
  ```

#### `POST /api/accounts`
* **Auth:** Bearer Token.
* **Descripción:** Crea una cuenta. `kind` posibles: `CHECKING`, `CASH`, `WALLET`, `SAVINGS`, `INVESTMENT`.
* **Body:**
  ```json
  {
    "name": "Mercado Pago",
    "kind": "WALLET",
    "currency": "ARS",
    "openingBalance": 45000.00,
    "openingDate": "2026-11-01",
    "color": "#06b6d4"
  }
  ```
* **Respuesta (201 Created):** Objeto `AccountResponse`.

#### `PATCH /api/accounts/{id}`
* **Auth:** Bearer Token.
* **Path Params:** `id` (UUID de la cuenta).
* **Body:**
  ```json
  {
    "name": "Mercado Pago Rendimientos",
    "kind": "SAVINGS",
    "color": "#0891b2",
    "openingBalance": 50000.00,
    "openingDate": "2026-11-01",
    "archived": false
  }
  ```
* **Respuesta (200 OK):** Objeto `AccountResponse`.

#### `DELETE /api/accounts/{id}`
* **Auth:** Bearer Token.
* **Path Params:** `id` (UUID).
* **Descripción:** Elimina la cuenta únicamente si no posee movimientos. Si ya tiene movimientos, debe archivarse usando `PATCH`.
* **Respuesta:** `204 No Content` (o `409 Conflict` si tiene movimientos).

#### `GET /api/accounts/{id}/movements`
* **Auth:** Bearer Token.
* **Path Params:** `id` (UUID).
* **Query Params:** `limit` (opcional, entero, por defecto 100).
* **Descripción:** Obtiene los movimientos cronológicos (pagos, cobros, transferencias, ajustes).
* **Respuesta (200 OK):** Array de `MovementResponse`.

#### `POST /api/accounts/{id}/adjustments`
* **Auth:** Bearer Token.
* **Path Params:** `id` (UUID).
* **Descripción:** Registra un ajuste de saldo manual por conciliación.
* **Body:**
  ```json
  {
    "targetBalance": 185000.00,
    "date": "2026-11-22",
    "notes": "Ajuste fin de semana"
  }
  ```
* **Respuesta (201 Created):** `{"id": "UUID"}`.

#### `DELETE /api/adjustments/{id}`
* **Auth:** Bearer Token.
* **Descripción:** Deshace y borra un ajuste de saldo.
* **Respuesta:** `204 No Content`.

#### `POST /api/transfers`
* **Auth:** Bearer Token.
* **Descripción:** Realiza una transferencia entre dos cuentas del usuario.
* **Body:**
  ```json
  {
    "fromAccountId": "UUID_ORIGEN",
    "toAccountId": "UUID_DESTINO",
    "amount": 25000.00,
    "toAmount": 25000.00,
    "rate": 1.0,
    "date": "2026-11-22",
    "notes": "Paso de fondos a ahorro"
  }
  ```
* **Respuesta (201 Created):** `{"id": "UUID", "fromAmount": 25000.00, "toAmount": 25000.00, "rateType": ""}`.

#### `DELETE /api/transfers/{id}`
* **Auth:** Bearer Token.
* **Descripción:** Deshace una transferencia.
* **Respuesta:** `204 No Content`.

---

### 04. Tarjetas de Crédito (`/api/cards`)

#### `GET /api/cards`
* **Auth:** Bearer Token.
* **Descripción:** Lista todas las tarjetas del usuario.
* **Respuesta (200 OK):**
  ```json
  [
    {
      "id": "4a71b9c1-4c17-4560-84a1-87bb1228f73a",
      "name": "Visa Santander",
      "color": "#dc2626",
      "archived": false
    }
  ]
  ```

#### `POST /api/cards`
* **Auth:** Bearer Token.
* **Body:**
  ```json
  {
    "name": "Mastercard BBVA",
    "color": "#1e40af"
  }
  ```
* **Respuesta (201 Created):** Objeto `CardResponse`.

#### `PATCH /api/cards/{id}`
* **Auth:** Bearer Token.
* **Body:** `{"name": "Mastercard Black", "color": "#172554", "archived": false}`.
* **Respuesta (200 OK):** Objeto `CardResponse`.

#### `DELETE /api/cards/{id}`
* **Auth:** Bearer Token.
* **Descripción:** Archiva la tarjeta (borrado suave para preservar el historial).
* **Respuesta:** `204 No Content`.

---

### 05. Categorías (`/api/categories`)

#### `GET /api/categories`
* **Auth:** Bearer Token.
* **Query Params:** `includeDeleted` (`true`/`false`, defecto `false`).
* **Respuesta (200 OK):**
  ```json
  [
    {
      "id": "e839e160-5f21-4f1b-967b-1cb81c0db935",
      "systemKey": "HOUSING",
      "name": "Vivienda",
      "color": "#3b82f6",
      "icon": "home",
      "system": true,
      "deleted": false
    }
  ]
  ```

#### `POST /api/categories`
* **Auth:** Bearer Token.
* **Body:** `{"name": "Gimnasio y Deportes", "color": "#10b981", "icon": "dumbbell"}`.
* **Respuesta (201 Created):** Objeto `CategoryResponse`.

#### `PATCH /api/categories/{id}`
* **Auth:** Bearer Token.
* **Body:** `{"name": "Salud y Fitness", "color": "#059669", "icon": "heart"}`.
* **Respuesta (200 OK):** Objeto `CategoryResponse`.

#### `DELETE /api/categories/{id}`
* **Auth:** Bearer Token.
* **Descripción:** Borrado lógico de la categoría. Las categorías de sistema (`system: true`) retornan `403 Forbidden`.
* **Respuesta:** `204 No Content`.

---

### 06. Ingresos y Cobros (`/api/incomes`)

#### `GET /api/incomes`
* **Auth:** Bearer Token.
* **Descripción:** Lista los ingresos configurados con sus versiones de monto.
* **Respuesta (200 OK):** Array de `IncomeResponse`.

#### `POST /api/incomes`
* **Auth:** Bearer Token.
* **Body:**
  ```json
  {
    "description": "Sueldo Desarrollo",
    "kind": "FIXED",
    "currency": "ARS",
    "amount": 1200000.00,
    "startMonth": "2026-11",
    "months": null,
    "adjustEveryMonths": 3,
    "adjustPercent": 10.0,
    "adjustWithInflation": false
  }
  ```
* **Respuesta (201 Created):** Objeto `IncomeResponse`.

#### `PATCH /api/incomes/{id}`
* **Auth:** Bearer Token.
* **Body:**
  ```json
  {
    "description": "Sueldo Principal",
    "endMonth": "2027-12",
    "clearEnd": false,
    "adjustEveryMonths": 6,
    "adjustPercent": 15.0,
    "adjustWithInflation": true,
    "clearAdjustment": false
  }
  ```
* **Respuesta (200 OK):** Objeto `IncomeResponse`.

#### `POST /api/incomes/{id}/amounts`
* **Auth:** Bearer Token.
* **Descripción:** Registra un nuevo monto a partir de un mes de vigencia.
* **Body:** `{"effectiveMonth": "2026-12", "amount": 1350000.00}`.
* **Respuesta (200 OK):** Objeto `IncomeResponse`.

#### `GET /api/incomes/{id}/receipts`
* **Auth:** Bearer Token.
* **Query Params:** `month` (obligatorio, formato `AAAA-MM`, ej: `2026-11`).
* **Respuesta (200 OK):** Lista de cobros registrados para ese ingreso en el mes.

#### `POST /api/incomes/{id}/receipts`
* **Auth:** Bearer Token.
* **Descripción:** Registra la acreditación / cobro de un ingreso en una cuenta específica.
* **Body:**
  ```json
  {
    "month": "2026-11",
    "accountId": "UUID_CUENTA",
    "amount": 1200000.00,
    "date": "2026-11-05",
    "rate": null,
    "notes": "Cobro sueldo transferencia"
  }
  ```
* **Respuesta (201 Created):** Array con los cobros del mes actualizados.

#### `DELETE /api/incomes/receipts/{id}`
* **Auth:** Bearer Token.
* **Descripción:** Borra un cobro realizado.
* **Respuesta:** `204 No Content`.

#### `DELETE /api/incomes/{id}`
* **Auth:** Bearer Token.
* **Descripción:** Borra un ingreso. Si tiene cobros asociados, retorna `409 Conflict`.
* **Respuesta:** `204 No Content`.

---

### 07. Gastos y Ocurrencias (`/api/expenses`, `/api/occurrences`)

#### `GET /api/expenses`
* **Auth:** Bearer Token.
* **Descripción:** Lista las plantillas de gasto con cuotas restantes, monto total y próximo mes de ajuste.
* **Respuesta (200 OK):** Array de `ExpenseResponse`.

#### `POST /api/expenses`
* **Auth:** Bearer Token.
* **Body (Gasto Recurrente / Servicios):**
  ```json
  {
    "description": "Internet Fibra",
    "categoryId": "UUID_CATEGORIA",
    "kind": "RECURRING",
    "currency": "ARS",
    "amount": 35000.00,
    "startMonth": "2026-11",
    "dueDay": 10,
    "variableAmount": false,
    "adjustEveryMonths": 3,
    "adjustPercent": 12.0,
    "adjustWithInflation": false,
    "notes": "Plan 300 megas",
    "cardId": null,
    "pastMonthsPaid": true
  }
  ```
* **Body (Cuotas con Tarjeta de Crédito):**
  ```json
  {
    "description": "Monitor 27 pulgadas",
    "categoryId": "UUID_CATEGORIA",
    "kind": "INSTALLMENTS",
    "currency": "ARS",
    "amount": 45000.00,
    "startMonth": "2026-11",
    "installments": 6,
    "cardId": "UUID_TARJETA",
    "pastMonthsPaid": false
  }
  ```
* **Respuesta (201 Created):** Objeto `ExpenseResponse`.

#### `PATCH /api/expenses/{id}`
* **Auth:** Bearer Token.
* **Body:** Permite modificar descripción, categoría, tarjeta (`cardId` o `clearCard`), día de vencimiento (`dueDay: 0` para último día del mes), reglas de aumento y fin del gasto.
* **Respuesta (200 OK):** Objeto `ExpenseResponse`.

#### `POST /api/expenses/{id}/amounts`
* **Auth:** Bearer Token.
* **Descripción:** Registra un cambio de monto futuro en la plantilla de gasto.
* **Body:** `{"effectiveMonth": "2027-01", "amount": 42000.00}`.
* **Respuesta (200 OK):** Objeto `ExpenseResponse`.

#### `DELETE /api/expenses/{id}`
* **Auth:** Bearer Token.
* **Descripción:** Borra la plantilla y sus ocurrencias sin pagos. Si tiene pagos registrados, retorna `409 Conflict`.
* **Respuesta:** `204 No Content`.

#### `PATCH /api/occurrences/{id}`
* **Auth:** Bearer Token.
* **Path Params:** `id` (UUID de la ocurrencia mensual).
* **Descripción:** Edita puntualmente una ocurrencia de un mes (ej. monto de la factura de luz o fecha real de vencimiento).
* **Body:**
  ```json
  {
    "expectedAmount": 38200.50,
    "dueDate": "2026-11-15",
    "notes": "Factura con recargo de verano"
  }
  ```
* **Respuesta (200 OK):** Objeto `OccurrenceResponse`.

---

### 08. Pagos y Comprobantes (`/api/occurrences/{id}/payments`, `/api/attachments`)

#### `GET /api/occurrences/{id}/payments`
* **Auth:** Bearer Token.
* **Descripción:** Obtiene todos los pagos parciales o totales de una ocurrencia con sus comprobantes y saldo pendiente.
* **Respuesta (200 OK):** Objeto `OccurrencePayments`.

#### `POST /api/occurrences/{id}/payments`
* **Auth:** Bearer Token.
* **Descripción:** Registra un pago descontando los fondos de la cuenta seleccionada.
* **Body:**
  ```json
  {
    "accountId": "UUID_CUENTA",
    "amount": 38200.50,
    "date": "2026-11-15",
    "rate": null,
    "notes": "Pago desde app bancaria"
  }
  ```
* **Respuesta (201 Created):** Objeto `OccurrencePayments` con el estado actualizado (`PAID`, `PARTIAL`, etc.).

#### `DELETE /api/payments/{id}`
* **Auth:** Bearer Token.
* **Descripción:** Elimina el pago, restituye el saldo en la cuenta y elimina los comprobantes asociados en Supabase Storage.
* **Respuesta (200 OK):** Objeto `OccurrencePayments`.

#### `POST /api/payments/{id}/attachments`
* **Auth:** Bearer Token.
* **Content-Type:** `multipart/form-data`.
* **Form Field:** `files` (hasta 10 archivos de hasta 10MB c/u).
* **Descripción:** Sube comprobantes al storage S3 (Supabase) vinculados al pago.
* **Respuesta (201 Created):** Lista de `AttachmentResponse`.

#### `GET /api/attachments/{id}`
* **Auth:** Bearer Token.
* **Descripción:** Descarga el archivo de comprobante con su tipo MIME original y cabecera `inline`.
* **Respuesta (200 OK):** Stream binario del archivo.

#### `DELETE /api/attachments/{id}`
* **Auth:** Bearer Token.
* **Descripción:** Borra el comprobante de la base de datos y de Supabase Storage.
* **Respuesta:** `204 No Content`.

---

### 09. Vista Mensual y Dashboard (`/api/months/{month}`)

#### `GET /api/months/{month}`
* **Auth:** Bearer Token.
* **Path Params:** `month` (`AAAA-MM` o `current`).
* **Descripción:** Endpoint principal del dashboard: balance consolidado por moneda (`totals`), desglose por categoría (`byCategory`), lista de ingresos y cobros (`incomes`), ocurrencias de gastos con su estado (`occurrences`) y resumen de vencidos (`overdue`).
* **Respuesta (200 OK):**
  ```json
  {
    "month": "2026-11",
    "projected": false,
    "totals": [
      {
        "currency": "ARS",
        "income": 1200000.00,
        "expenses": 450000.00,
        "paid": 350000.00,
        "pending": 100000.00,
        "overdue": 0.00,
        "balance": 750000.00
      }
    ],
    "byCategory": [],
    "incomes": [],
    "occurrences": [],
    "overdue": []
  }
  ```

---

### 10. Historial y Comparativas (`/api/history`)

#### `GET /api/history`
* **Auth:** Bearer Token.
* **Query Params:**
  * `from`: Mes inicial (`AAAA-MM`).
  * `to`: Mes final (`AAAA-MM`).
  * `currency`: `ARS` o `USD`.
  * `categories`: Lista de UUIDs de categorías para filtrar gastos.
* **Respuesta (200 OK):** Serie histórica de ahorro, ingresos y gastos, ajustados por inflación oficial (`realFactor`, `totalSavingsReal`).

#### `GET /api/history/compare`
* **Auth:** Bearer Token.
* **Query Params:**
  * `from`: Mes base (`AAAA-MM`).
  * `to`: Mes a comparar (`AAAA-MM`, por defecto actual).
  * `currency`: `ARS` o `USD`.
* **Respuesta (200 OK):** Comparación detallada ítem por ítem (`UP`, `DOWN`, `SAME`, `NEW`, `ENDED`) y por categoría.

---

### 11. Proyecciones Financieras (`/api/projections`)

#### `GET /api/projections`
* **Auth:** Bearer Token.
* **Query Params:**
  * `months`: Horizonte de simulación (1 a 120, por defecto 12).
  * `currency`: `ARS` o `USD`.
  * `inflationPercent`: Tasa mensual personalizada en % (0 a 50).
  * `startBalance`: Capital disponible inicial.
* **Respuesta (200 OK):** Serie proyectada nominal y en poder adquisitivo constante (`cumulativeReal`), con impacto de cuotas y ajustes inflacionarios.

---

### 12. Inflación (`/api/inflation`)

#### `GET /api/inflation`
* **Auth:** Bearer Token.
* **Descripción:** Datos oficiales de inflación (INDEC para ARS, IPC BLS para USD), estimación y tasa manual activa.
* **Respuesta (200 OK):** Objeto `InflationOverview`.

#### `PUT /api/inflation/{currency}/manual`
* **Auth:** Bearer Token.
* **Path Params:** `currency` (`ARS` o `USD`).
* **Body:** `{"monthlyPercent": 3.5}`.
* **Respuesta (200 OK):** `InflationOverview` actualizado.

#### `DELETE /api/inflation/{currency}/manual`
* **Auth:** Bearer Token.
* **Descripción:** Elimina la tasa manual para que el sistema vuelva a calcular la tasa estimada automática.
* **Respuesta:** `204 No Content`.

---

### 13. Cotizaciones de Dólar (`/api/exchange-rates`)

#### `GET /api/exchange-rates`
* **Auth:** Bearer Token.
* **Descripción:** Últimas cotizaciones sincronizadas de DolarAPI (Oficial, Blue, MEP, Tarjeta, Mayorista, Cripto).
* **Respuesta (200 OK):** Array con `type`, `day`, `buy`, `sell`.

#### `GET /api/exchange-rates/convert`
* **Auth:** Bearer Token.
* **Query Params:**
  * `amount`: Monto numérico.
  * `from`: Moneda origen (`USD`/`ARS`).
  * `to`: Moneda destino (`ARS`/`USD`).
  * `purpose`: Propósito de la conversión (`PAYMENT`, `PAYMENT_CARD`, `RECEIPT`, `TRANSFER`, `VALUATION`).
  * `rate`: Tasa manual opcional.
* **Respuesta (200 OK):** Objeto `Conversion` con monto convertido y tipo de cambio aplicado.

---

### 14. Línea de Tiempo (`/api/timeline`)

#### `GET /api/timeline`
* **Auth:** Bearer Token.
* **Query Params:**
  * `from`: Mes inicial (`AAAA-MM`).
  * `months`: Cantidad de meses (1 a 120, por defecto 13).
  * `currency`: `ARS` o `USD`.
* **Descripción:** Provee los puntos para el gráfico interactivo del Home (pasado + presente + futuro proyectado).

---

### 15. Sistema / Actuator (`/actuator`)

#### `GET /actuator/health`
* **Auth:** Pública.
* **Descripción:** Estado de salud del contenedor Cloud Run, conexión a base de datos y espacio en disco.
* **Respuesta (200 OK):** `{"status": "UP"}`.

---

## 3. Instrucciones de uso en Postman

1. Abre **Postman**.
2. Haz clic en **Import** y selecciona el archivo generado:
   `saver_postman_collection.json`
3. La colección ya incluye la variable de entorno `baseUrl` configurada con tu URL de Cloud Run:
   `https://saver-backend-5585932474.southamerica-east1.run.app`
4. Ejecuta el request **01 - Autenticación → Iniciar sesión (Login)** o **Registrar usuario**.
   * La colección tiene un script automático que captura el `accessToken` devuelto y lo guarda en la variable de colección `{{token}}`.
5. A partir de ese momento, todas las solicitudes protegidas quedan autenticadas automáticamente con el Bearer Token.
