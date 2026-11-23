# Despliegue

[English](../en/deployment.md) · [← Volver](../../README.md)

Arquitectura objetivo en GCP:

| Componente | Servicio |
|---|---|
| API | **Cloud Run** (contenedor de este repo) |
| Base de datos | **Supabase** PostgreSQL (o Cloud SQL) |
| Comprobantes | **Supabase Storage** durante el demo, mediante la API S3 — ver [Configuración](configuracion.md#almacenamiento-de-comprobantes) |
| Secretos | **Secret Manager** |
| Imágenes | **Artifact Registry** |
| Frontend | Firebase Hosting, con rewrite de `/api/**` a este servicio (ver `frontend/`) |

## Imagen Docker

```bash
docker build -t saver-backend .
docker run --rm -p 8080:8080 --env-file .env -e DB_HOST=host.docker.internal -e STORAGE_ENDPOINT=http://host.docker.internal:8333 saver-backend
```

El `Dockerfile` compila con Maven y ejecuta en `eclipse-temurin:21-jre-alpine` con un usuario sin privilegios. La imagen no guarda archivos en disco: los comprobantes van siempre al object storage configurado (Cloud Run tiene filesystem efímero).

## Cloud Run (primera vez)

```bash
PROJECT=portfolio-slk2023
REGION=southamerica-east1          # São Paulo, la más cercana a Argentina
REPO=saver

gcloud artifacts repositories create $REPO --repository-format=docker --location=$REGION

# Secretos
printf '%s' "$(openssl rand -base64 48)" | gcloud secrets create saver-jwt-secret --data-file=-
printf '%s' 'PASSWORD_DE_SUPABASE'      | gcloud secrets create saver-db-password --data-file=-

# Build y push
gcloud builds submit --tag $REGION-docker.pkg.dev/$PROJECT/$REPO/backend:latest

# Deploy
gcloud run deploy saver-backend \
  --image $REGION-docker.pkg.dev/$PROJECT/$REPO/backend:latest \
  --region $REGION --allow-unauthenticated \
  --memory 512Mi --min-instances 0 --max-instances 2 \
  --set-env-vars DB_HOST=<host-pooler-de-sesion>,DB_PORT=5432,DB_NAME=postgres,DB_USER=<usuario-copiado-de-supabase>,DB_SSLMODE=require,SPRING_DATASOURCE_HIKARI_MAXIMUM_POOL_SIZE=5 \
  --set-env-vars COOKIE_SECURE=true,APP_ALLOWED_ORIGINS=https://<dominio-hosting>,APP_WEB_URL=https://<dominio-hosting>,GOOGLE_CLIENT_ID=xxxx.apps.googleusercontent.com \
  --set-env-vars STORAGE_ENVIRONMENT=production,STORAGE_PROVIDER=s3-compatible,STORAGE_ENDPOINT=<endpoint-s3-copiado-de-supabase>,STORAGE_BUCKET=saver-receipts,STORAGE_REGION=<region-copiada-de-supabase> \
  --set-secrets JWT_SECRET=saver-jwt-secret:latest,DB_PASSWORD=saver-db-password:latest,STORAGE_ACCESS_KEY=saver-storage-key:latest,STORAGE_SECRET_KEY=saver-storage-secret:latest
```

> La cuenta de servicio de Cloud Run necesita el rol `roles/secretmanager.secretAccessor`.

## Base de datos Supabase

- Usar la cadena de conexión **directa** (puerto 5432) o el *session pooler*. El *transaction pooler* (6543) no es compatible con los prepared statements de JDBC sin configuración extra.
- Flyway aplica las migraciones al arrancar el contenedor.

## Supabase Storage

1. En Supabase, abrir **Storage** y crear manualmente el bucket privado `saver-receipts`. En producción, Saver no crea buckets automáticamente.
2. En **Storage → Configuration → S3**, habilitar el protocolo S3, generar un par de claves y copiar el endpoint y la región que muestra Supabase. No inventar la región ni publicar las claves.
3. Guardar el Access Key ID como `saver-storage-key` y el Secret Access Key como `saver-storage-secret` en Secret Manager; conceder a la cuenta de servicio de Cloud Run acceso a ambos secretos.
4. Configurar `STORAGE_PROVIDER=s3-compatible`, `STORAGE_ENVIRONMENT=production`, `STORAGE_ENDPOINT` con el endpoint S3 copiado, `STORAGE_BUCKET=saver-receipts` y `STORAGE_REGION` con la región copiada. Las claves se inyectan como `STORAGE_ACCESS_KEY` y `STORAGE_SECRET_KEY`.

El bucket debe permanecer privado. Saver valida el acceso al archivo en el backend y entrega enlaces de descarga firmados con vencimiento. La API S3 de Supabase es compatible con el cliente que ya usa Saver; no hace falta agregar el SDK de Supabase al frontend. Las claves S3 son de servidor y pueden saltarse las políticas RLS: nunca ponerlas en el navegador ni en `VITE_*`.

## Tareas programadas con varias instancias

Si `max-instances > 1`, las tareas `@Scheduled` correrían en cada instancia. Antes de la etapa 3 (cierre de mes) se agregará ShedLock o se moverán a Cloud Scheduler.

## CI/CD (sugerido)

GitHub Actions: `mvn verify` en cada PR, y en `main` construir la imagen, subirla a Artifact Registry y desplegar con `google-github-actions/deploy-cloudrun` usando Workload Identity Federation (sin claves JSON).
