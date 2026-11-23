# Saver — Backend

🇪🇸 Español · [🇬🇧 English](README.en.md)

API REST de **Saver**, una aplicación para gestionar gastos, ingresos, ahorros y simular inversiones.
Forma parte del repositorio [Saver](../README.md); el frontend está en [`../frontend`](../frontend/README.md).

| | |
|---|---|
| Lenguaje | Java 21 |
| Framework | Spring Boot 3.5 (Web, Security, Data JPA, Validation, Actuator) |
| Autenticación | JWT propio (HS256) + refresh token rotado en cookie HttpOnly + Google Identity |
| Base de datos | PostgreSQL 16 · migraciones con Flyway |
| Archivos | API S3: SeaweedFS en Docker (local, sin internet) · proveedor de producción a definir (Supabase Storage, R2, GCS, AWS S3) |
| Documentación API | OpenAPI 3 · Swagger UI en `/swagger-ui.html` |
| Despliegue | Docker · Google Cloud Run |

## Inicio rápido

Requisitos: JDK 21, Maven 3.9+, Docker Desktop.

```powershell
Copy-Item .env.example .env      # Linux/macOS: cp .env.example .env
docker compose up -d             # PostgreSQL (:5433) + SeaweedFS (:8333) + Mailpit (:8025)
mvn spring-boot:run              # backend en http://localhost:8080 (o Run desde IntelliJ)
```

Docker solo levanta la infraestructura; el backend se ejecuta con Maven o IntelliJ (en IntelliJ, el *working directory* debe ser esta carpeta para que lea el `.env`). Una vez descargadas las imágenes, todo funciona sin internet.

- Emails de desarrollo (Mailpit): http://localhost:8025
- Health check: http://localhost:8080/actuator/health
- Swagger UI: http://localhost:8080/swagger-ui.html
- Los comprobantes de desarrollo viven en el volumen Docker `saver_objectdata`. Al arrancar en desarrollo, el backend crea el bucket si no existe.

El backend lee el archivo `.env` de esta carpeta automáticamente. Flyway crea y actualiza el esquema al arrancar.

Detener conservando datos: `docker compose down`.

Borrar exclusivamente los objetos multimedia locales: `powershell -ExecutionPolicy Bypass -File scripts/clean-storage.ps1`. El script pide confirmación y no elimina `saver_pgdata`.

## Tests

```powershell
mvn test
```

Con `docker compose up -d` levantado, también corren los tests de integración (base `saver_test`); sin la base, se omiten.

## Estructura

```
src/main/java/com/saver/
├── auth/       # Registro, login, Google, refresh y logout
├── user/       # Perfil del usuario (/api/me), UserTime (mes actual según zona horaria)
├── notification/ # Preferencias de avisos por tipo y canal
├── category/   # Categorías de sistema y propias (borrado lógico)
├── income/     # Ingresos fijos y extras con montos versionados
├── expense/    # Plantillas de gasto, ocurrencias mensuales, ExpenseSchedule (cálculos)
├── month/      # Vista del mes: balance, vencidos
├── history/    # Historial mensual y comparación entre meses
├── projection/ # Proyección a N meses (nominal y en valores de hoy)
├── inflation/  # Inflación oficial ARS/USD (descarga diaria), estimada y manual
├── exchange/   # Cotizaciones del dólar (dolarapi) y conversiones según el uso
├── account/    # Cuentas, saldos, transferencias, ajustes y patrimonio
├── card/       # Tarjetas (agrupan gastos)
├── payment/    # Pagos de ocurrencias y comprobantes
├── timeline/   # Serie pasado + futuro del inicio
├── mail/       # Puerto MailSender + adaptadores (log, Mailpit, Brevo)
├── security/   # Spring Security, JWT, @CurrentUser
├── config/     # Propiedades tipadas (app.*), reloj
├── storage/    # Puerto StorageService + adaptador S3 (SeaweedFS, Supabase, R2, AWS…)
└── common/     # Errores (ApiException, ProblemDetail)
src/main/resources/
├── application.yml
└── db/migration/   # Scripts Flyway V{n}__descripcion.sql
```

## Documentación

| Tema | Descripción |
|---|---|
| [Arquitectura](docs/es/arquitectura.md) | Capas, paquetes y decisiones técnicas |
| [Dominio](docs/es/dominio.md) | Reglas de negocio: ingresos, gastos, cuotas, cierres, inversiones |
| [Configuración](docs/es/configuracion.md) | Variables de entorno y perfiles |
| [Autenticación](docs/es/autenticacion.md) | Flujo JWT, refresh token y Google |
| [API](docs/es/api.md) | Endpoints y formato de errores |
| [Base de datos](docs/es/base-de-datos.md) | Esquema, convenciones y migraciones |
| [Despliegue](docs/es/despliegue.md) | Docker, Cloud Run y Supabase |
| [Convenciones](docs/es/convenciones.md) | Código, commits y ramas |

## Roadmap

1. ✅ Base: autenticación, perfil, i18n de errores, entorno local
2. ✅ Ingresos, categorías, gastos (único, cuotas, fijos, variables) y balance mensual
3. ✅ (3A) Cuentas, tarjetas, cotizaciones del dólar, pagos parciales con comprobantes y cobros · (3B) cierre automático de mes versionado, ahorro real, recordatorios por email
4. ✅ Historial, comparaciones entre meses y proyecciones · ✅ recuperación de contraseña por email · ✅ inflación oficial por moneda y línea de tiempo del inicio
5. Simulador de inversiones con escenarios guardados
6. Informes PDF y exportación Excel/CSV · despliegue en GCP
