# Estrategia de despliegue e integraciones

Esta guía propone un camino reproducible para el estado actual de Saver. Los comandos usan sintaxis de PowerShell cuando corresponde. Reemplazar los valores entre `<...>`; no pegar claves reales en scripts, historial de consola o repositorios.

## 1. Decisiones de arquitectura

| Entorno | Frontend | API | Base de datos | Archivos |
|---|---|---|---|---|
| Local | Vite (`:5173`) | Spring Boot (`:8080`) | PostgreSQL Docker (`:5433`) | SeaweedFS Docker con API S3 (`:8333`) |
| Producción para el demo | Firebase Hosting | Cloud Run (`southamerica-east1`) | Supabase PostgreSQL | Supabase Storage (API S3) |

Se recomienda Firebase Hosting + Cloud Run con el rewrite ya presente en `frontend/firebase.json`: el navegador llama a `/api`, por lo que UI y API comparten origen. Así funciona `SameSite=Strict` y la cookie de refresh debe conservar el nombre `__session`; Firebase Hosting permite reenviar esa cookie a Cloud Run. [Firebase documenta esta restricción](https://firebase.google.com/docs/hosting/manage-cache?authuser=683989221).

**Estado del proyecto actual:** `frontend/.firebaserc` apunta a `portfolio-slk2023` (Blaze). `frontend/firebase.json` ya reescribe `/api/**` hacia `saver-backend` en `southamerica-east1`; ese servicio todavía debe desplegarse y configurarse con PostgreSQL, storage y correo antes de que la app quede operativa.

## 2. Preparar y verificar localmente

1. Instalar JDK 21, Maven 3.9+, Node.js 20+, Docker Desktop y, para despliegue, Google Cloud CLI y Firebase CLI.
2. En `backend`, crear la configuración local:

   ```powershell
   Copy-Item .env.example .env
   ```

   Cambiar `JWT_SECRET` por un valor aleatorio de al menos 32 caracteres. Mantener `COOKIE_SECURE=false` solo en local.

3. Iniciar dependencias:

   ```powershell
   cd backend
   docker compose up -d
   mvn spring-boot:run
   ```

4. En otra terminal:

   ```powershell
   cd frontend
   npm ci
   npm run dev
   ```

5. Verificar `http://localhost:8080/actuator/health`, `http://localhost:8080/swagger-ui.html` y `http://localhost:5173`. Vite reenvía `/api` al backend.
6. Antes de integrar, ejecutar `npm test`, `npm run build` y `mvn verify`.

Docker Compose levanta solo la infraestructura (PostgreSQL y SeaweedFS, publicados en `127.0.0.1`); el backend se ejecuta con Maven o IntelliJ. Los datos viven en los volúmenes `saver_pgdata` y `saver_objectdata`. En desarrollo el backend crea el bucket si no existe. Una vez descargadas las imágenes, el entorno funciona sin internet.

## 3. Elegir y configurar PostgreSQL

### Opción A: Supabase (más rápida para comenzar)

1. Crear un proyecto Supabase en una región cercana al público y guardar la contraseña de base de datos en un gestor seguro.
2. En **Connect**, copiar host, puerto, usuario y nombre de base para el modo elegido; no deducir el host a mano.
3. Para Cloud Run, que habitualmente sale por IPv4, usar el **shared pooler en session mode** (puerto 5432) salvo que se contrate IPv4 para conexión directa. Evitar `transaction mode` (6543) con la configuración actual de JDBC: no admite prepared statements salvo que se agregue `prepareThreshold=0` y se pruebe exhaustivamente.
4. Cargar los valores en Secret Manager (contraseña) y variables no secretas (`DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USER`). Configurar SSL en JDBC al usar un host externo; por ejemplo, incluir `?sslmode=require` en la URL o ampliar `application.yml` para una variable `DB_SSLMODE` antes del despliegue.
5. El primer arranque de la API ejecutará Flyway. Confirmar que creó `users` y `refresh_tokens` y registrar la versión en `flyway_schema_history`.

La selección anterior sigue la recomendación de Supabase: conexión directa para backends persistentes con IPv6/IPv4 add-on, y pooler compartido en modo sesión para backends persistentes desde redes IPv4. Ver [guía de conexiones](https://supabase.com/docs/guides/database/connecting-to-postgres).

### Opción B: Cloud SQL PostgreSQL (máxima integración GCP)

1. Habilitar Cloud SQL Admin API y crear una instancia PostgreSQL en la misma región que Cloud Run.
2. Crear base `saver` y un usuario de aplicación con privilegios solo sobre esa base; no usar el superusuario en runtime.
3. Crear una cuenta de servicio dedicada para Cloud Run y otorgarle `roles/cloudsql.client` sobre la instancia. Usar Secret Manager para la contraseña.
4. **Trabajo pendiente de implementación:** este backend solo forma URL JDBC por host/puerto. Para usar el socket seguro `/cloudsql/<PROJECT>:<REGION>:<INSTANCE>` hay que agregar el Cloud SQL Java Connector o permitir que `DB_HOST` acepte una URL completa; luego desplegar el servicio con `--add-cloudsql-instances`. No declarar Cloud SQL listo hasta completar y probar esa adaptación.
5. Limitar conexiones de Hikari y `max-instances`; cada instancia de Cloud Run abre conexiones y Cloud SQL tiene límites. Google recomienda ubicar ambos en la misma región y asignar `Cloud SQL Client` a la identidad del servicio. [Guía oficial](https://cloud.google.com/sql/docs/postgres/connect-run).

## 4. Configurar el proyecto GCP paso a paso

1. Crear o seleccionar el proyecto, asociar facturación y elegir `southamerica-east1` salvo que los requisitos de datos indiquen otra región.
2. Autenticarse y establecer variables de sesión:

   ```powershell
   gcloud auth login
   gcloud config set project <PROJECT_ID>
   $PROJECT_ID = gcloud config get-value project
   $REGION = 'southamerica-east1'
   ```

3. Habilitar servicios requeridos:

   ```powershell
   gcloud services enable run.googleapis.com artifactregistry.googleapis.com cloudbuild.googleapis.com secretmanager.googleapis.com iamcredentials.googleapis.com
   ```

   Agregar `sqladmin.googleapis.com` si se eligió Cloud SQL. Para Firebase, crear/agregar Firebase al mismo proyecto desde Firebase Console.

4. Crear un repositorio Docker y una identidad de ejecución de mínimo privilegio:

   ```powershell
   gcloud artifacts repositories create saver --repository-format=docker --location=$REGION
   gcloud iam service-accounts create saver-run --display-name='Saver Cloud Run'
   ```

5. Crear secretos. Generar el JWT fuera del repositorio y crear versiones explícitas (por ejemplo `:1`), no depender de `latest` para variables de entorno.

   ```powershell
   gcloud secrets create saver-jwt-secret --replication-policy=automatic
   gcloud secrets versions add saver-jwt-secret --data-file=<archivo-seguro-con-el-secreto>
   gcloud secrets create saver-db-password --replication-policy=automatic
   gcloud secrets versions add saver-db-password --data-file=<archivo-seguro-con-password>
   gcloud secrets add-iam-policy-binding saver-jwt-secret --member="serviceAccount:saver-run@$PROJECT_ID.iam.gserviceaccount.com" --role='roles/secretmanager.secretAccessor'
   gcloud secrets add-iam-policy-binding saver-db-password --member="serviceAccount:saver-run@$PROJECT_ID.iam.gserviceaccount.com" --role='roles/secretmanager.secretAccessor'
   ```

   Repetir para credenciales futuras. Cloud Run recomienda Secret Manager y la identidad de servicio necesita `Secret Manager Secret Accessor`. [Documentación](https://cloud.google.com/run/docs/configuring/services/secrets).

6. Configurar Google Sign-In si se usará: crear un OAuth Client ID de tipo Web, agregar `http://localhost:5173` para desarrollo y después las URLs HTTPS de Firebase y dominio propio como orígenes autorizados. Guardar solo el Client ID como `GOOGLE_CLIENT_ID`; no existe client secret utilizado por este flujo.

## 5. Desplegar API en Cloud Run

Desde `backend` y tras aprobar pruebas:

```powershell
gcloud builds submit --tag "$REGION-docker.pkg.dev/$PROJECT_ID/saver/backend:<VERSION>"
gcloud run deploy saver-backend `
  --image "$REGION-docker.pkg.dev/$PROJECT_ID/saver/backend:<VERSION>" `
  --region $REGION `
  --service-account "saver-run@$PROJECT_ID.iam.gserviceaccount.com" `
  --allow-unauthenticated `
  --memory 512Mi --min-instances 0 --max-instances 2 `
  --set-env-vars "DB_HOST=<HOST>,DB_PORT=<PORT>,DB_NAME=<NAME>,DB_USER=<USER>,COOKIE_SECURE=true,APP_ALLOWED_ORIGINS=https://<PROJECT_ID>.web.app,GOOGLE_CLIENT_ID=<CLIENT_ID>" `
  --set-secrets "JWT_SECRET=saver-jwt-secret:1,DB_PASSWORD=saver-db-password:1"
```

`--allow-unauthenticated` es necesario porque Firebase Hosting debe poder invocar la API; la protección de las rutas privadas sigue siendo JWT. Verificar `/actuator/health`, `/api/auth/providers` y los logs de Flyway antes de continuar. Con Supabase, confirmar que el acceso de red y TLS llegan correctamente. Con Cloud SQL, agregar la conexión de instancia solo luego de la adaptación indicada en la sección anterior.

## 6. Desplegar el frontend en Firebase Hosting

1. En `frontend`, iniciar sesión y vincular el proyecto:

   ```powershell
   npm ci
   npm run build
   firebase login
   firebase use --add
   ```

2. Revisar `firebase.json`: `serviceId` debe ser `saver-backend` y `region` debe coincidir exactamente con Cloud Run. Mantener `VITE_API_URL` vacío para usar `/api` en mismo origen.
3. Publicar y validar:

   ```powershell
   firebase deploy --only hosting
   ```

4. Abrir la URL resultante, crear una cuenta de prueba, refrescar la página, cerrar sesión y probar Google si está habilitado. Verificar en DevTools que las solicitudes se hagan a `/api` y que la cookie `__session` sea `HttpOnly`, `Secure` y `SameSite=Strict`.

## 7. Ciclo seguro de cada despliegue

1. Crear versión inmutable (`<VERSION>` = commit SHA o etiqueta), ejecutar pruebas y revisión de migraciones.
2. Desplegar primero API con nueva revisión, observar salud, logs, errores de autenticación y Flyway.
3. Ejecutar pruebas de humo: registro, login, refresh tras recargar, `GET/PATCH /api/me`, logout y login Google si aplica.
4. Publicar frontend que consume esa API. No activar funciones financieras hasta que exista compatibilidad de API y migración.
5. Observar métricas y logs. Mantener el tag y la revisión anterior para revertir.
6. Reversión: devolver tráfico de Cloud Run a la revisión previa (`gcloud run services update-traffic ... --to-revisions <REVISION>=100`) y volver a desplegar el build anterior de Hosting. Las migraciones deben ser aditivas y compatibles hacia atrás; no revertir datos destructivamente como mecanismo rutinario.

## 8. CI/CD, seguridad y siguiente etapa

### Pendiente: habilitar Google Sign-In

Google Sign-In queda deliberadamente deshabilitado durante esta primera puesta en marcha. Una vez verificados backend, Firebase Hosting, PostgreSQL, storage y correo, crear/configurar un Client ID OAuth web, agregar `https://saver-app-demo.web.app` como origen autorizado y desplegar una revisión con `GOOGLE_CLIENT_ID`. No guardar un client secret: este backend valida el ID token que entrega Google.

- En cada PR: `npm ci && npm test && npm run build`, `mvn verify`, análisis de dependencias y revisión de migraciones.
- En `main`: construir una imagen con SHA, desplegar mediante Workload Identity Federation (sin claves JSON permanentes), ejecutar smoke tests y recién entonces publicar Hosting.
- Rotar JWT y contraseña de DB creando una nueva versión de secreto y desplegando una revisión que la fije; planificar el impacto de rotar JWT sobre sesiones existentes.
- Configurar alertas por errores 5xx, salud no disponible, aumento de latencia y errores de conexión a DB. Respaldar PostgreSQL y ensayar restauración.
- **Almacenamiento de comprobantes:** para el demo se eligió Supabase Storage junto con Supabase PostgreSQL. El backend ya tiene un adaptador S3 compatible, así que basta configurar el endpoint y región S3 de Supabase, y guardar sus claves en Secret Manager. El bucket debe crearse manualmente y permanecer privado. Para migrar a AWS, el backend ya tiene adaptador S3; GCS nativo requiere implementar un adaptador. No exponer claves al frontend: las descargas usan URLs firmadas de corta duración.
