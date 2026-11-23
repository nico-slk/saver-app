# Configuración

[English](../en/configuration.md) · [← Volver](../../README.md)

La configuración se toma de **variables de entorno**. En desarrollo, el archivo `backend/.env` se carga automáticamente (`spring.config.import: optional:file:.env[.properties]`), por eso hay que ejecutar Maven desde esta carpeta.

> ⚠️ Nunca commitear `.env`. En producción los secretos van en **Google Secret Manager**.

## Variables

| Variable | Por defecto | Descripción |
|---|---|---|
| `PORT` | `8080` | Puerto HTTP (Cloud Run lo inyecta) |
| `DB_HOST` | `localhost` | Host de PostgreSQL |
| `DB_PORT` | `5433` | Puerto de PostgreSQL en el host. Docker Compose publica ahí el 5432 del contenedor; se usa 5433 porque el 5432 suele estar ocupado por un PostgreSQL instalado. En producción, el del proveedor (Supabase: 5432) |
| `DB_NAME` | `saver` | Base de datos |
| `DB_USER` | `saver` | Usuario |
| `DB_PASSWORD` | `saver` | Contraseña |
| `JWT_SECRET` | *(valor de desarrollo)* | Secreto HS256, **mínimo 32 caracteres**. Obligatorio cambiarlo en producción |
| `JWT_ACCESS_TTL_MINUTES` | `15` | Vida del access token |
| `JWT_REFRESH_TTL_DAYS` | `30` | Vida del refresh token |
| `GOOGLE_CLIENT_ID` | *(vacío)* | Client ID OAuth web. Vacío = login con Google deshabilitado |
| `APP_ALLOWED_ORIGINS` | `http://localhost:5173` | Orígenes CORS, separados por coma |
| `COOKIE_SECURE` | `false` | `true` en producción (HTTPS) |
| `STORAGE_PROVIDER` | `s3-compatible` | `s3-compatible`: cualquier servicio con API S3 y endpoint propio (SeaweedFS local, Supabase Storage, R2, GCS con claves HMAC). `s3`: AWS S3 nativo. `gcs`: reservado para un adaptador nativo. `filesystem`: carpeta local (`STORAGE_ENDPOINT` = ruta, por defecto `data/storage`), solo desarrollo sin Docker. |
| `STORAGE_ENVIRONMENT` | `development` | `development` crea el bucket al arrancar. `production` exige endpoint HTTPS remoto y credenciales que no sean las de ejemplo. |
| `STORAGE_ENDPOINT` | `http://localhost:8333` | API S3 de SeaweedFS publicada por Docker Compose. Vacío para AWS S3. |
| `STORAGE_BUCKET` | `saver-dev-media` | Bucket privado. En desarrollo el backend lo crea si no existe. |
| `STORAGE_REGION` | `us-east-1` | Región requerida por el cliente S3. |
| `STORAGE_ACCESS_KEY` | `saver-dev` | Credencial S3. Con `s3` (AWS) no se usa: se toma la identidad del entorno. |
| `STORAGE_SECRET_KEY` | `saver-dev-secret` | Credencial S3. Los valores de ejemplo se rechazan en producción. |
| `APP_WEB_URL` | `http://localhost:5173` | URL pública del frontend; se usa en los links de los emails |
| `MAIL_PROVIDER` | `log` | `log` (no envía, lo escribe en el log), `mailpit` (Docker local) o `brevo` (envío real) |
| `MAIL_FROM_ADDRESS` | `no-reply@saver.local` | Remitente. Con Brevo debe estar verificado en la cuenta |
| `MAIL_FROM_NAME` | `Saver` | Nombre del remitente |
| `MAIL_MAILPIT_URL` | `http://localhost:8025` | API de Mailpit (solo `mailpit`) |
| `MAIL_BREVO_API_KEY` | *(vacío)* | API key de Brevo (solo `brevo`). Secreto: en producción, Secret Manager |
| `INFLATION_SYNC_ENABLED` | `true` | Descarga diaria de la inflación oficial. `false` = no se descarga (se usa la manual o 0) |
| `INFLATION_SYNC_ON_STARTUP` | `true` | También descargar al arrancar |
| `INFLATION_ESTIMATE_MONTHS` | `3` | Meses oficiales que se promedian para estimar la inflación futura |
| `INFLATION_ARGENTINADATOS_URL` | `https://api.argentinadatos.com` | Fuente de la inflación ARS (INDEC) |
| `INFLATION_BLS_URL` | `https://api.bls.gov` | Fuente de la inflación USD (IPC de EE.UU.) |
| `INFLATION_BLS_API_KEY` | *(vacío)* | Opcional: clave gratuita de BLS (500 consultas/día en lugar de 25) |
| `EXCHANGE_SYNC_ENABLED` | `true` | Descarga de cotizaciones del dólar (dolarapi.com). `false` = se cargan a mano |
| `EXCHANGE_SYNC_ON_STARTUP` | `true` | También descargar al arrancar |
| `EXCHANGE_DOLARAPI_URL` | `https://dolarapi.com` | Fuente de las cotizaciones |
| `STORAGE_MAX_FILE_SIZE_BYTES` | `10485760` | Tamaño máximo por archivo (10 MiB). Debe coincidir con `spring.servlet.multipart.max-file-size`. |

Generar un secreto seguro:

```bash
openssl rand -base64 48
```

## Propiedades de la aplicación (`app.*`)

Están tipadas y validadas al arrancar en `config/AppProperties.java`. Si falta un valor obligatorio, la aplicación no inicia.

```yaml
app:
  jwt:      { secret, issuer, access-ttl-minutes, refresh-ttl-days }
  google:   { client-id }
  cors:     { allowed-origins }
  cookie:   { secure, same-site }
  storage:  { provider, environment, endpoint, bucket, region, access-key, secret-key, max-file-size-bytes }
  web:      { base-url }
  mail:     { provider, from-address, from-name, mailpit-url, brevo-api-key }
  inflation: { enabled, sync-on-startup, estimate-months, argentina-datos-url, bls-url, bls-api-key }
  exchange:  { enabled, sync-on-startup, dolar-api-url }
  defaults: { timezone, currency, locale }
```

## Almacenamiento de comprobantes

El código depende solo de la interfaz `StorageService`; el proveedor se elige por configuración, así que **el de producción se puede decidir más adelante sin tocar la lógica de negocio**.

| Escenario | `STORAGE_PROVIDER` | `STORAGE_ENDPOINT` | Credenciales |
|---|---|---|---|
| Local (Docker, sin internet) | `s3-compatible` | `http://localhost:8333` | Las de `.env.example` |
| Supabase Storage | `s3-compatible` | `https://<proyecto>.supabase.co/storage/v1/s3` | *S3 access keys* del proyecto |
| Cloudflare R2 | `s3-compatible` | `https://<cuenta>.r2.cloudflarestorage.com` | Token S3 de R2 |
| Google Cloud Storage (interoperable) | `s3-compatible` | `https://storage.googleapis.com` | Claves HMAC de una cuenta de servicio |
| AWS S3 | `s3` | *(vacío)* | Identidad IAM del entorno |
| Local sin Docker | `filesystem` | `data/storage` (ruta) | — (no se permite en producción) |

En producción usar `STORAGE_ENVIRONMENT=production` y guardar las claves en Secret Manager. Para un proveedor sin API S3 (o GCS nativo), implementar `StorageService` y registrarlo en `StorageConfiguration`.

## Envío de emails

Se usa para la recuperación de contraseña. El código depende de la interfaz `MailSender`; el proveedor se elige con `MAIL_PROVIDER`, así que más adelante se puede cambiar por el servicio de la nube elegida (Amazon SES, SendGrid, etc.) sumando un adaptador, sin tocar el resto.

| Proveedor | Para qué | Costo | Configuración |
|---|---|---|---|
| `log` | Sin Docker ni internet | — | Nada: el email (con el link) aparece en la consola del backend |
| `mailpit` | Desarrollo local | Gratis, sin internet | `docker compose up -d` y abrir **http://localhost:8025** para ver los emails |
| `brevo` | Envío real | Gratis hasta 300 emails/día | Crear cuenta en [brevo.com](https://www.brevo.com), verificar el remitente (*Senders*), crear una API key (*SMTP & API → API Keys*) y cargar `MAIL_BREVO_API_KEY` y `MAIL_FROM_ADDRESS` |

Los adaptadores usan el `HttpClient` del JDK (sin dependencias extra). El envío es asíncrono y posterior al commit: si el proveedor falla, se registra en el log y la respuesta al usuario no cambia.

## Inflación oficial

El backend descarga la inflación una vez por día (07:20, hora de Buenos Aires) y al arrancar. Sin internet no falla: registra un aviso y sigue con los datos guardados o con la inflación manual de cada usuario. En producción con varias instancias conviene moverlo a un job programado (Cloud Scheduler) igual que el cierre de mes.

## Login con Google

1. En [Google Cloud Console → Credenciales](https://console.cloud.google.com/apis/credentials) crear un **ID de cliente OAuth** de tipo *Aplicación web*.
2. En *Orígenes autorizados de JavaScript* agregar `http://localhost:5173` y el dominio de producción.
3. Copiar el Client ID en `GOOGLE_CLIENT_ID` y reiniciar el backend.

No hace falta *client secret*: el backend solo valida el ID token que entrega Google.
