# Configuration

[Español](../es/configuracion.md) · [← Back](../../README.en.md)

Configuration comes from **environment variables**. In development, the `backend/.env` file is loaded automatically (`spring.config.import: optional:file:.env[.properties]`), so Maven must be run from this folder.

> ⚠️ Never commit `.env`. In production, secrets live in **Google Secret Manager**.

## Variables

| Variable | Default | Description |
|---|---|---|
| `PORT` | `8080` | HTTP port (injected by Cloud Run) |
| `DB_HOST` | `localhost` | PostgreSQL host |
| `DB_PORT` | `5433` | PostgreSQL port on the host. Docker Compose publishes the container's 5432 there; 5433 is used because 5432 is often taken by a locally installed PostgreSQL. In production, the provider's port (Supabase: 5432) |
| `DB_NAME` | `saver` | Database |
| `DB_USER` | `saver` | User |
| `DB_PASSWORD` | `saver` | Password |
| `JWT_SECRET` | *(dev value)* | HS256 secret, **at least 32 characters**. Must be changed in production |
| `JWT_ACCESS_TTL_MINUTES` | `15` | Access token lifetime |
| `JWT_REFRESH_TTL_DAYS` | `30` | Refresh token lifetime |
| `GOOGLE_CLIENT_ID` | *(empty)* | Web OAuth Client ID. Empty = Google sign-in disabled |
| `APP_ALLOWED_ORIGINS` | `http://localhost:5173` | CORS origins, comma-separated |
| `COOKIE_SECURE` | `false` | `true` in production (HTTPS) |
| `STORAGE_PROVIDER` | `s3-compatible` | `s3-compatible`: any S3-API service with its own endpoint (local SeaweedFS, Supabase Storage, R2, GCS with HMAC keys). `s3`: native AWS S3. `gcs`: reserved for a native adapter. `filesystem`: local folder (`STORAGE_ENDPOINT` = path, default `data/storage`), development without Docker only. |
| `STORAGE_ENVIRONMENT` | `development` | `development` creates the bucket on startup. `production` requires a remote HTTPS endpoint and non-sample credentials. |
| `STORAGE_ENDPOINT` | `http://localhost:8333` | SeaweedFS S3 API published by Docker Compose. Empty for AWS S3. |
| `STORAGE_BUCKET` | `saver-dev-media` | Private bucket. In development the backend creates it if missing. |
| `STORAGE_REGION` | `us-east-1` | Region required by the S3 client. |
| `STORAGE_ACCESS_KEY` | `saver-dev` | S3 credential. Not used with `s3` (AWS): the environment identity is used. |
| `STORAGE_SECRET_KEY` | `saver-dev-secret` | S3 credential. Sample values are rejected in production. |
| `APP_WEB_URL` | `http://localhost:5173` | Public frontend URL; used for links in emails |
| `MAIL_PROVIDER` | `log` | `log` (doesn't send, writes to the log), `mailpit` (local Docker) or `brevo` (real sending) |
| `MAIL_FROM_ADDRESS` | `no-reply@saver.local` | Sender. With Brevo it must be verified in the account |
| `MAIL_FROM_NAME` | `Saver` | Sender name |
| `MAIL_MAILPIT_URL` | `http://localhost:8025` | Mailpit API (`mailpit` only) |
| `MAIL_BREVO_API_KEY` | *(empty)* | Brevo API key (`brevo` only). Secret: Secret Manager in production |
| `INFLATION_SYNC_ENABLED` | `true` | Daily download of official inflation. `false` = no download (manual rate or 0 is used) |
| `INFLATION_SYNC_ON_STARTUP` | `true` | Also download on startup |
| `INFLATION_ESTIMATE_MONTHS` | `3` | Official months averaged to estimate future inflation |
| `INFLATION_ARGENTINADATOS_URL` | `https://api.argentinadatos.com` | ARS inflation source (INDEC) |
| `INFLATION_BLS_URL` | `https://api.bls.gov` | USD inflation source (US CPI) |
| `INFLATION_BLS_API_KEY` | *(empty)* | Optional free BLS key (500 queries/day instead of 25) |
| `EXCHANGE_SYNC_ENABLED` | `true` | Dollar rates download (dolarapi.com). `false` = entered manually |
| `EXCHANGE_SYNC_ON_STARTUP` | `true` | Also download on startup |
| `EXCHANGE_DOLARAPI_URL` | `https://dolarapi.com` | Rates source |
| `STORAGE_MAX_FILE_SIZE_BYTES` | `10485760` | Max size per file (10 MiB). Keep it aligned with `spring.servlet.multipart.max-file-size`. |

Generate a secure secret:

```bash
openssl rand -base64 48
```

## Application properties (`app.*`)

They are typed and validated at startup in `config/AppProperties.java`. If a required value is missing, the app does not start.

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

## Receipt storage

Code depends only on the `StorageService` interface; the provider is chosen by configuration, so **the production provider can be decided later without touching business logic**.

| Scenario | `STORAGE_PROVIDER` | `STORAGE_ENDPOINT` | Credentials |
|---|---|---|---|
| Local (Docker, offline) | `s3-compatible` | `http://localhost:8333` | Those in `.env.example` |
| Supabase Storage | `s3-compatible` | `https://<project>.supabase.co/storage/v1/s3` | Project *S3 access keys* |
| Cloudflare R2 | `s3-compatible` | `https://<account>.r2.cloudflarestorage.com` | R2 S3 token |
| Google Cloud Storage (interoperable) | `s3-compatible` | `https://storage.googleapis.com` | Service account HMAC keys |
| AWS S3 | `s3` | *(empty)* | Environment IAM identity |
| Local without Docker | `filesystem` | `data/storage` (path) | — (not allowed in production) |

In production use `STORAGE_ENVIRONMENT=production` and keep keys in Secret Manager. For a provider without an S3 API (or native GCS), implement `StorageService` and register it in `StorageConfiguration`.

## Sending emails

Used for password recovery. Code depends on the `MailSender` interface; the provider is picked with `MAIL_PROVIDER`, so it can later be swapped for the chosen cloud's service (Amazon SES, SendGrid, etc.) by adding an adapter, without touching the rest.

| Provider | Use | Cost | Setup |
|---|---|---|---|
| `log` | No Docker or internet | — | Nothing: the email (with the link) shows up in the backend console |
| `mailpit` | Local development | Free, offline | `docker compose up -d` and open **http://localhost:8025** to see the emails |
| `brevo` | Real sending | Free up to 300 emails/day | Create an account at [brevo.com](https://www.brevo.com), verify the sender (*Senders*), create an API key (*SMTP & API → API Keys*) and set `MAIL_BREVO_API_KEY` and `MAIL_FROM_ADDRESS` |

Adapters use the JDK `HttpClient` (no extra dependencies). Sending is asynchronous and happens after commit: if the provider fails, it is logged and the user's response doesn't change.

## Official inflation

The backend downloads inflation once a day (07:20 Buenos Aires time) and on startup. Without internet it does not fail: it logs a warning and keeps using the stored data or each user's manual rate. In production with several instances it is better moved to a scheduled job (Cloud Scheduler), like the month closing.

## Google sign-in

1. In [Google Cloud Console → Credentials](https://console.cloud.google.com/apis/credentials) create an **OAuth client ID** of type *Web application*.
2. Under *Authorized JavaScript origins* add `http://localhost:5173` and the production domain.
3. Copy the Client ID into `GOOGLE_CLIENT_ID` and restart the backend.

No *client secret* is needed: the backend only validates the ID token issued by Google.
