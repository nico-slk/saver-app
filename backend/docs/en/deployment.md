# Deployment

[Español](../es/despliegue.md) · [← Back](../../README.en.md)

Target architecture on GCP:

| Component | Service |
|---|---|
| API | **Cloud Run** (this repo's container) |
| Database | **Supabase** PostgreSQL (or Cloud SQL) |
| Receipts | **Supabase Storage** for the demo, using its S3 API — see [Configuration](configuration.md#receipt-storage) |
| Secrets | **Secret Manager** |
| Images | **Artifact Registry** |
| Frontend | Firebase Hosting, rewriting `/api/**` to this service (see `frontend/`) |

## Docker image

```bash
docker build -t saver-backend .
docker run --rm -p 8080:8080 --env-file .env -e DB_HOST=host.docker.internal -e STORAGE_ENDPOINT=http://host.docker.internal:8333 saver-backend
```

The `Dockerfile` builds with Maven and runs on `eclipse-temurin:21-jre-alpine` as a non-root user. The image keeps no files on disk: receipts always go to the configured object storage (Cloud Run has an ephemeral filesystem).

## Cloud Run (first time)

```bash
PROJECT=portfolio-slk2023
REGION=southamerica-east1          # São Paulo, closest to Argentina
REPO=saver

gcloud artifacts repositories create $REPO --repository-format=docker --location=$REGION

# Secrets
printf '%s' "$(openssl rand -base64 48)" | gcloud secrets create saver-jwt-secret --data-file=-
printf '%s' 'SUPABASE_PASSWORD'         | gcloud secrets create saver-db-password --data-file=-

# Build and push
gcloud builds submit --tag $REGION-docker.pkg.dev/$PROJECT/$REPO/backend:latest

# Deploy
gcloud run deploy saver-backend \
  --image $REGION-docker.pkg.dev/$PROJECT/$REPO/backend:latest \
  --region $REGION --allow-unauthenticated \
  --memory 512Mi --min-instances 0 --max-instances 2 \
  --set-env-vars DB_HOST=<session-pooler-host>,DB_PORT=5432,DB_NAME=postgres,DB_USER=<username-from-supabase>,DB_SSLMODE=require,SPRING_DATASOURCE_HIKARI_MAXIMUM_POOL_SIZE=5 \
  --set-env-vars COOKIE_SECURE=true,APP_ALLOWED_ORIGINS=https://<hosting-domain>,APP_WEB_URL=https://<hosting-domain>,GOOGLE_CLIENT_ID=xxxx.apps.googleusercontent.com \
  --set-env-vars STORAGE_ENVIRONMENT=production,STORAGE_PROVIDER=s3-compatible,STORAGE_ENDPOINT=<s3-endpoint-copied-from-supabase>,STORAGE_BUCKET=saver-receipts,STORAGE_REGION=<region-copied-from-supabase> \
  --set-secrets JWT_SECRET=saver-jwt-secret:latest,DB_PASSWORD=saver-db-password:latest,STORAGE_ACCESS_KEY=saver-storage-key:latest,STORAGE_SECRET_KEY=saver-storage-secret:latest
```

> The Cloud Run service account needs the `roles/secretmanager.secretAccessor` role.

## Supabase database

- Use the **direct** connection string (port 5432) or the *session pooler*. The *transaction pooler* (6543) is not compatible with JDBC prepared statements without extra configuration.
- Flyway applies migrations when the container starts.

## Supabase Storage

1. In Supabase, open **Storage** and manually create a private `saver-receipts` bucket. Saver does not create production buckets automatically.
2. Under **Storage → Configuration → S3**, enable the S3 protocol, generate a key pair, and copy the endpoint and region shown by Supabase. Do not guess the region or expose the keys.
3. Store the Access Key ID as `saver-storage-key` and the Secret Access Key as `saver-storage-secret` in Secret Manager. Grant the Cloud Run service account access to both secrets.
4. Configure `STORAGE_PROVIDER=s3-compatible`, `STORAGE_ENVIRONMENT=production`, `STORAGE_ENDPOINT` with the copied S3 endpoint, `STORAGE_BUCKET=saver-receipts`, and `STORAGE_REGION` with the copied region. Inject the keys as `STORAGE_ACCESS_KEY` and `STORAGE_SECRET_KEY`.

Keep the bucket private. Saver checks access through its backend and returns expiring signed download links. Supabase's S3 API works with Saver's existing client, so no Supabase SDK is needed in the frontend. S3 keys are server-side credentials that can bypass RLS; never put them in the browser or in `VITE_*` variables.

## Scheduled jobs with multiple instances

If `max-instances > 1`, `@Scheduled` jobs would run on every instance. Before stage 3 (month closing), ShedLock will be added or jobs will move to Cloud Scheduler.

## CI/CD (suggested)

GitHub Actions: `mvn verify` on every PR, and on `main` build the image, push it to Artifact Registry and deploy with `google-github-actions/deploy-cloudrun` using Workload Identity Federation (no JSON keys).
