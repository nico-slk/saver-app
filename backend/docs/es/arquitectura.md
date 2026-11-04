# Arquitectura

[English](../en/architecture.md) · [← Volver](../../README.md)

## Visión general

```
┌──────────────────────┐   HTTPS /api/*    ┌──────────────────────────┐
│  saver-frontend      │ ────────────────▶ │  saver-backend (Spring)  │
│  React SPA           │ ◀──────────────── │  Cloud Run               │
│  Firebase Hosting    │   JSON / Problem  └──────┬─────────┬─────────┘
└──────────────────────┘                          │         │
                                                  ▼         ▼
                                      PostgreSQL (Supabase)   Object storage S3
                                                              (SeaweedFS local)
```

- **Stateless**: no hay sesión HTTP. Cada request se autentica con un access token JWT.
- **Multiusuario aislado**: cada recurso pertenece a un `user_id` y toda consulta se filtra por el usuario autenticado (`@CurrentUser`). Nunca se confía en un `userId` que venga en el body o en la URL.
- **Idioma**: el backend no devuelve textos traducidos. Los errores llevan un `code` estable (`auth.invalid_credentials`) que traduce el frontend. Los informes PDF usarán el `locale` del perfil.

## Paquetes

Se organiza **por funcionalidad** (feature packages), no por capa técnica. Cada paquete contiene su controller, servicio, entidades, repositorios y DTOs.

| Paquete | Responsabilidad |
|---|---|
| `auth` | Registro, login, Google, emisión y rotación de tokens |
| `user` | Perfil y preferencias (`/api/me`) |
| `security` | Cadena de filtros, codificador/decodificador JWT, anotación `@CurrentUser` |
| `config` | `AppProperties` (prefijo `app.*`), `Clock` inyectable |
| `common` | `ApiException`, `GlobalExceptionHandler` |
| `storage` | Puerto `StorageService` y adaptador S3 (SeaweedFS en local; proveedor de producción configurable). Claves UUID, URLs firmadas |

Paquetes previstos en las próximas etapas: `category`, `income`, `expense`, `payment`, `attachment`, `closing`, `exchange`, `projection`, `investment`, `report`.

## Decisiones técnicas

| Decisión | Motivo |
|---|---|
| Montos con `BigDecimal` / `NUMERIC(15,2)` | Evitar errores de redondeo de `double` |
| Fechas en `Instant` / `timestamptz` (UTC) | El “mes” se calcula con la zona horaria del usuario (por defecto `America/Argentina/Buenos_Aires`) |
| `Clock` inyectable | Testear vencimientos, cierres de mes y expiración de tokens |
| Flyway + `ddl-auto: validate` | El esquema se versiona en SQL; Hibernate solo valida que coincida |
| Errores RFC 9457 (`ProblemDetail`) | Formato estándar con `code` para i18n y `fields` para validaciones |
| Adjuntos en object storage detrás de un puerto | En DB solo va la metadata (base64 inflaría la base ~33 %). La interfaz `StorageService` permite cambiar de proveedor sin tocar el dominio |
| Records para DTOs y propiedades | Inmutabilidad y menos código |
| Sin Lombok | Menos magia en compilación; los records cubren la mayoría de los casos |

## Tareas programadas

`@EnableScheduling` está activo. Tareas actuales y previstas:

| Tarea | Cuándo | Etapa |
|---|---|---|
| Purga de refresh tokens vencidos | Diario 03:30 (ART) | 1 ✅ |
| Cierre automático de mes | Fin de cada mes, por zona horaria del usuario | 3 |
| Actualización de cotización USD | Diario | 3 |

En Cloud Run, si se escala a más de una instancia, las tareas deberán coordinarse (ShedLock o Cloud Scheduler llamando a un endpoint interno).
