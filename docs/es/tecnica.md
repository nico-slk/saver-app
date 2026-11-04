# Saver: análisis técnico

## Vista de conjunto

Saver está dividido en dos aplicaciones independientes dentro de este directorio:

```text
Navegador
  └─ frontend/  React + TypeScript + Vite
       └─ /api/* ── desarrollo: proxy Vite ──┐
                                             ├─ backend/ Spring Boot
       └─ producción: Firebase Hosting ──────┘      └─ PostgreSQL
                                                    └─ Google Identity (opcional)
```

El frontend entrega la interfaz. El backend expone una API REST, aplica autenticación y guarda perfiles y sesiones. PostgreSQL conserva los datos. En local, Docker Compose levanta PostgreSQL y SeaweedFS (object storage con API S3); el backend corre con Maven o IntelliJ. El paquete `storage` expone la interfaz `StorageService` con un adaptador S3, pero aún no existen endpoints de comprobantes que lo consuman.

## Componentes y responsabilidades

| Área | Tecnología | Responsabilidad actual |
|---|---|---|
| `frontend/` | React 19, TypeScript, Vite, Tailwind | Rutas, formularios, preferencias visuales, textos ES/EN y consumo de API. |
| Estado del cliente | Zustand + TanStack Query | Zustand guarda sesión y tema; TanStack Query consulta proveedores de acceso. |
| `backend/` | Java 21, Spring Boot 3.5 | API REST, validación, seguridad, reglas de cuentas y perfil. |
| Datos | PostgreSQL 16, JPA, Flyway | Tablas `users` y `refresh_tokens`; migraciones versionadas. |
| Acceso | JWT HS256, BCrypt, cookie HttpOnly | Access token corto en memoria; refresh token opaco, rotado, en cookie. |
| Identidad externa | Google Identity Services / JWKS de Google | Verifica el ID token recibido del cliente cuando `GOOGLE_CLIENT_ID` está definido. |
| Operación | Docker, Cloud Run, Firebase Hosting | Imagen del backend y entrega de la SPA. |

## Estructura útil

```text
frontend/src/
  features/auth/      pantallas y cliente de autenticación
  stores/             sesión y preferencias locales
  lib/api.ts          Axios, idioma y renovación de token
  pages/              dashboard y ajustes
  i18n/               textos es/en
backend/src/main/
  java/com/saver/auth/     registro, login, refresh, Google
  java/com/saver/user/     perfil y persistencia de usuario
  java/com/saver/security/ filtros, JWT y CORS
  resources/db/migration/  esquema Flyway
  resources/application.yml configuración por variables
```

## Flujos implementados

### Registro o inicio de sesión

1. El navegador envía las credenciales a `POST /api/auth/register` o `POST /api/auth/login`.
2. El backend valida el contenido, normaliza el correo y, en registro, guarda la contraseña con BCrypt.
3. Emite un JWT de acceso (por defecto, 15 minutos) en la respuesta y un refresh token opaco en la cookie HttpOnly `__session` (por defecto, 30 días).
4. El frontend mantiene el JWT solo en el store de memoria y utiliza `Authorization: Bearer ...` para las rutas protegidas.

### Recuperación y renovación de sesión

1. Al iniciar la SPA, `authStore.bootstrap()` llama a `POST /api/auth/refresh`.
2. El navegador envía la cookie automáticamente porque Axios usa `withCredentials`.
3. El backend busca únicamente el hash SHA-256 del refresh token, lo revoca y crea uno nuevo. Si detecta reutilización, revoca todas las sesiones de la persona.
4. El frontend coordina renovaciones simultáneas entre solicitudes y pestañas mediante una promesa compartida y Web Locks cuando el navegador lo admite.
5. Un `401` de una ruta protegida provoca una única renovación y reintento. Si falla, se limpia el estado y se vuelve a `/login`.

### Acceso con Google

1. El frontend consulta `GET /api/auth/providers`; muestra el botón solamente si recibe un `googleClientId`.
2. Google entrega un ID token al navegador, que se envía a `POST /api/auth/google`.
3. El backend verifica firma, expiración, emisor, audiencia y correo verificado usando las claves públicas de Google.
4. Después enlaza o crea el usuario y ejecuta la misma emisión de sesión que en el login tradicional.

### Perfil y preferencias

`GET /api/me` y `PATCH /api/me` requieren JWT. El servidor valida zona horaria, moneda y texto; el frontend actualiza el usuario en Zustand. El tema se guarda localmente en el navegador; idioma, moneda y zona horaria se almacenan en `users`.

## API existente

| Método y ruta | Requiere sesión | Uso |
|---|---:|---|
| `GET /api/auth/providers` | No | Informa métodos habilitados. |
| `POST /api/auth/register` | No | Registra y abre sesión. |
| `POST /api/auth/login` | No | Abre sesión con contraseña. |
| `POST /api/auth/google` | No | Abre sesión usando ID token de Google. |
| `POST /api/auth/refresh` | Cookie | Rota y renueva la sesión. |
| `POST /api/auth/logout` | Cookie opcional | Revoca la sesión y borra la cookie. |
| `GET /api/me` | JWT | Devuelve el perfil. |
| `PATCH /api/me` | JWT | Actualiza nombre, idioma, moneda o zona horaria. |
| `/api/categories`, `/api/incomes`, `/api/expenses`, `/api/occurrences/{id}` | JWT | Categorías, ingresos, plantillas de gasto y ajuste de un mes (detalle en `backend/docs/es/api.md`). |
| `GET /api/months/{AAAA-MM\|current}` | JWT | Balance del mes, vencimientos, vencidos y meses futuros proyectados. |
| `GET /api/history`, `GET /api/history/compare` | JWT | Historial mensual y comparación entre meses. |
| `GET /api/projections` | JWT | Proyección a N meses, nominal y en valores de hoy. |
| `GET /api/inflation`, `PUT/DELETE /api/inflation/{moneda}/manual` | JWT | Inflación oficial por moneda (descarga diaria: ArgentinaDatos/INDEC y BLS), estimada y manual. |
| `GET /api/timeline` | JWT | Serie pasado + proyección para el gráfico del inicio. |
| `POST /api/auth/password/forgot`, `/reset` | No | Recuperación de contraseña por email (Mailpit en local, Brevo gratuito para envío real). |

También están disponibles `GET /actuator/health`, Swagger en `/swagger-ui.html` y OpenAPI en `/v3/api-docs`.

## Datos y cambios de esquema

Flyway ejecuta los SQL de `backend/src/main/resources/db/migration` al comenzar. Hibernate valida el esquema pero no lo modifica (`ddl-auto: validate`). Para cualquier cambio de datos: crear un archivo nuevo `V{n}__descripcion.sql`; no editar una migración ya aplicada. La primera migración crea `users` y `refresh_tokens`, con UUID, marcas de tiempo UTC e índices para correo y tokens.

## Integraciones y límites actuales

- **Firebase Hosting → Cloud Run:** `frontend/firebase.json` redirige `/api/**` al servicio `saver-backend` en `southamerica-east1`. Esto mantiene mismo origen; Firebase solo reenvía hacia Cloud Run la cookie llamada `__session`, de ahí ese nombre.
- **CORS:** en desarrollo Vite redirige `/api` a `localhost:8080`. En producción con el rewrite no hace falta CORS entre SPA y API, pero el backend conserva una lista de orígenes configurables para escenarios de dominios separados.
- **Storage:** `StorageService` (puerto) + `S3StorageService` (adaptador). Claves UUID generadas por el servidor, límite de tamaño y URLs firmadas de hasta 1 hora. El proveedor se elige con `STORAGE_PROVIDER` (`s3-compatible` para SeaweedFS/Supabase/R2/GCS interoperable, `s3` para AWS). No hay endpoints de archivos ni metadata de adjuntos aún; cualquier endpoint futuro debe verificar que el archivo pertenezca al usuario antes de abrirlo.
- **Tareas programadas:** existe una limpieza diaria de refresh tokens. Con más de una instancia, cada instancia podría ejecutarla; antes de agregar cierres de negocio se recomienda ShedLock o Cloud Scheduler.

## Controles operativos y pruebas

- Frontend: `npm run typecheck`, `npm test`, `npm run build`.
- Backend: `mvn test` y `mvn verify`.
- Salud: `/actuator/health`.
- Errores: el backend devuelve `ProblemDetail` sin stack traces; el frontend traduce los códigos de error conocidos.

Nunca incluir secretos en `VITE_*`, en el repositorio ni en imágenes. El `.env` local debe permanecer fuera del control de versiones.
