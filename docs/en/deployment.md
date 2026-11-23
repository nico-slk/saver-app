# Deployment and integration strategy

This is the reproducible operating path for the current Saver release. Replace `<...>` values and never put real credentials in source code, shell history, or images.

## Target architecture

| Environment | Frontend | API | Database | Files |
|---|---|---|---|---|
| Local | Vite (`:5173`) | Spring Boot (`:8080`) | Docker PostgreSQL (`:5433`) | Docker SeaweedFS with S3 API (`:8333`) |
| Demo production | Firebase Hosting | Cloud Run (`southamerica-east1`) | Supabase PostgreSQL | Supabase Storage (S3 API) |

Firebase Hosting rewrites `/api/**` to Cloud Run, keeping the app same-origin. Keep the refresh cookie named `__session`: Hosting forwards that special cookie to Cloud Run. [Firebase documentation](https://firebase.google.com/docs/hosting/manage-cache?authuser=683989221).

**Current project status:** `frontend/.firebaserc` targets `portfolio-slk2023` (Blaze). `frontend/firebase.json` rewrites `/api/**` to `saver-backend` in `southamerica-east1`; that service still needs to be deployed and configured with PostgreSQL, storage, and email before the app is operational.

## Local setup

1. Install JDK 21, Maven 3.9+, Node 20+, Docker Desktop, Google Cloud CLI, and Firebase CLI.
2. Run `Copy-Item .env.example .env` in `backend`, replace `JWT_SECRET`, and keep `COOKIE_SECURE=false` only locally.
3. Run `docker compose up -d; mvn spring-boot:run` in `backend`.
4. Run `npm ci; npm run dev` in `frontend`.
5. Check `/actuator/health`, Swagger, and `http://localhost:5173`; Vite proxies `/api`.
6. Before integration run `npm test`, `npm run build`, and `mvn verify`.

## PostgreSQL choice

### Supabase

Create a project, copy the selected endpoint from **Connect**, and keep the password in a secret manager. For Cloud Run's common IPv4 egress, use the shared **session pooler** at port 5432 unless direct IPv4 is available. Do not use transaction mode (6543) with the current JDBC setup: it needs `prepareThreshold=0` and dedicated testing. Require SSL for the external JDBC connection. Flyway applies the schema on first API startup. Supabase explains connection choices and pooler restrictions in its [connection guide](https://supabase.com/docs/guides/database/connecting-to-postgres).

### Cloud SQL

Enable Cloud SQL Admin API; create PostgreSQL in the same region as Cloud Run; create a least-privilege app database user; create a dedicated Cloud Run service account and grant `roles/cloudsql.client`; keep its password in Secret Manager. **Implementation prerequisite:** the current code builds JDBC URLs from host/port. Add the Cloud SQL Java Connector or full JDBC URL support before using `/cloudsql/...` with `--add-cloudsql-instances`; then test it. Cap application connections and Cloud Run instances. [Google's Cloud Run connection guide](https://cloud.google.com/sql/docs/postgres/connect-run) covers regional placement and IAM.

## GCP setup

1. Create/select a billed project and choose `southamerica-east1` unless data requirements dictate otherwise.
2. Authenticate and set `$PROJECT_ID` and `$REGION`.
3. Enable `run.googleapis.com`, `artifactregistry.googleapis.com`, `cloudbuild.googleapis.com`, `secretmanager.googleapis.com`, and `iamcredentials.googleapis.com`; add `sqladmin.googleapis.com` for Cloud SQL.
4. Create Artifact Registry repository `saver` and service account `saver-run`.
5. Create `saver-jwt-secret` and `saver-db-password`; grant `roles/secretmanager.secretAccessor` to the `saver-run` service account. Pin an explicit secret version for environment-variable secrets. [Cloud Run secret guidance](https://cloud.google.com/run/docs/configuring/services/secrets).
6. If needed, configure a Web OAuth client in Google Cloud: add local and production HTTPS origins, then set only `GOOGLE_CLIENT_ID`.

## Deploy sequence

1. From `backend`, build immutable image `gcloud builds submit --tag "$REGION-docker.pkg.dev/$PROJECT_ID/saver/backend:<VERSION>"`.
2. Deploy it to Cloud Run with the `saver-run` identity, `COOKIE_SECURE=true`, DB host/port/name/user, `APP_ALLOWED_ORIGINS`, optional `GOOGLE_CLIENT_ID`, and version-pinned `JWT_SECRET`/`DB_PASSWORD` secrets. Allow unauthenticated invocation because Firebase must reach the API; API authorization remains JWT-based.
3. Verify health, Flyway logs, registration, login, refresh, profile update, logout, and Google sign-in if enabled.
4. In `frontend`, run `npm ci`, `npm run build`, `firebase login`, `firebase use --add`, then `firebase deploy --only hosting`. Ensure `firebase.json` service and region exactly match Cloud Run and leave `VITE_API_URL` empty.
5. Test the hosted app: `/api` calls, secure `__session` cookie, reload/session restore, and sign-out.

## Release, rollback, and next steps

### Pending: enable Google Sign-In

Google Sign-In is deliberately disabled during this first rollout. After backend, Firebase Hosting, PostgreSQL, storage, and email have been verified, create/configure a Web OAuth client ID, add `https://saver-app-demo.web.app` as an authorized origin, and deploy a revision with `GOOGLE_CLIENT_ID`. Do not store a client secret: this backend validates the Google-issued ID token.

Deploy API first, run smoke tests, then publish frontend. Version images by commit SHA; retain the previous Cloud Run revision and Hosting build. Roll back Cloud Run traffic to the prior revision and redeploy the previous Hosting artifact if needed. Keep migrations additive and backward compatible; do not rely on destructive database rollback.

For CI/CD, validate frontend/backend on each pull request and deploy from `main` with Workload Identity Federation instead of long-lived JSON keys. Alert on health, 5xx errors, latency, and database connectivity; test restore procedures. Supabase Storage is the demo choice alongside Supabase PostgreSQL. The backend already has an S3-compatible adapter, so configure Supabase's S3 endpoint and region and keep its keys in Secret Manager. Create the bucket manually and keep it private. For an AWS migration, the backend already has an S3 adapter; native GCS needs its own adapter. Never expose keys to the frontend; downloads use short-lived signed URLs.
