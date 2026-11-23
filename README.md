# Saver

🇪🇸 Español · [🇬🇧 English](README.en.md)

> ⚠️ **Aviso Importante: Proyecto de Demostración**  
> Saver es una aplicación de demostración de bajo presupuesto (*proof of concept* / MVP) creada con fines de portfolio y prueba técnica. No cuenta con acuerdos de nivel de servicio (SLA) de alta disponibilidad ni auditorías de seguridad bancaria. **Se recomienda expresamente NO ingresar información financiera real, contraseñas sensibles ni datos personales confidenciales.**

---

Saver es una aplicación web responsive para organizar las finanzas personales en un solo lugar. Permite entender qué dinero entra, qué gastos hay que cubrir y cómo evolucionan las cuentas a lo largo del tiempo, facilitando la toma de decisiones cotidianas con mayor claridad.

## El problema que resuelve

Los ingresos, las cuotas de tarjetas, los servicios y los vencimientos suelen quedar dispersos entre notas, planillas de cálculo, recordatorios y aplicaciones bancarias. Eso dificulta saber con exactitud cuánto dinero queda disponible, qué compromisos vencen pronto y cómo impacta una compra en los meses siguientes. Saver centraliza esa información en un panel mensual intuitivo y relaciona cada movimiento con cuentas y fechas concretas.

## Qué ofrece la aplicación

- **Ingresos y Gastos:** Registro de ingresos fijos, extras y aumentos; gestión de gastos únicos, recurrentes y en cuotas con tarjeta.
- **Vista Mensual (Dashboard):** Balance consolidado por moneda (ARS/USD), control de vencimientos próximos, deudas pendientes y distribución de gastos por categoría.
- **Historial y Análisis Inflacionario:** Comparación mes a mes e investigación de variaciones en ingresos, gastos y ahorro, tanto en valores nominales como en poder adquisitivo real ajustado por índices de inflación (INDEC / BLS).
- **Simulador y Proyecciones:** Proyección financiera a N meses considerando saldo inicial, cuotas futuras y tasas de inflación estimadas o personalizadas.
- **Cuentas y Patrimonio:** Administración de cuentas bancarias y billeteras virtuales, seguimiento de tarjetas de crédito, conciliaciones/ajustes de saldo y transferencias entre cuentas.
- **Pagos y Comprobantes:** Registro de pagos parciales o totales y cobro de haberes, con conversión de divisas y adjuntos de comprobantes digitales (almacenamiento en la nube).
- **Seguridad y Acceso:** Autenticación por contraseña y Google Sign-In opcional, sesiones con tokens JWT y cookies `HttpOnly`, y recuperación de clave por correo electrónico.

---

## Tecnologías utilizadas

### Frontend
* **React 19** con **TypeScript**: Interfaz de usuario declarativa, moderna y tipada.
* **Vite**: Entorno de compilación y empaquetado optimizado.
* **Tailwind CSS v4**: Sistema de estilos basado en utilidades de alto rendimiento.
* **Zustand**: Gestión de estado global ligera para autenticación, alertas y tema visual.
* **TanStack Query (React Query)**: Caché asíncrono y sincronización de datos con la API.
* **React Router v7**: Enrutamiento del lado del cliente.
* **Recharts**: Gráficos interactivos de líneas de tiempo, balances y proyecciones.
* **i18next**: Internacionalización dinámica (Español e Inglés).
* **Axios**: Cliente HTTP con interceptores para renovación automática de tokens de sesión.

### Backend
* **Java 21 LTS**: Última versión de soporte extendido de la plataforma Java.
* **Spring Boot 3.5**:
  * **Spring Web (MVC)**: API RESTful estructurada bajo convenciones estándar.
  * **Spring Security**: Protección de rutas, validación de tokens JWT (`HS256`) y cookies de sesión `SameSite=Strict`.
  * **Spring Data JPA & Hibernate**: Capa de persistencia y mapeo relacional.
* **PostgreSQL 16**: Motor de base de datos relacional para datos financieros e integridad referencial.
* **Flyway**: Control de versiones y migraciones automáticas del esquema de base de datos.
* **AWS SDK v2 for Java**: Cliente compatible con el protocolo S3 para la gestión de comprobantes y archivos multimedia.
* **Spring Boot Actuator**: Métricas y monitoreo de salud del servicio (`/actuator/health`).

### Infraestructura y Servicios en la Nube
* **Google Cloud Run (`southamerica-east1` - São Paulo):** Alojamiento serverless en contenedores Docker para la API del backend, con escalado automático de 0 a 2 instancias para optimizar costos.
* **Firebase Hosting:** CDN global para el frontend estático, con reglas de *rewrite* (`/api/**`) hacia Cloud Run para compartir el mismo origen (habilitando cookies seguras sin bloqueos de CORS o terceros).
* **Supabase:**
  * **PostgreSQL Administrado**: Conexión a través del *Session Pooler* (puerto 5432).
  * **Supabase Storage**: Bucket privado con API compatible con Amazon S3 para comprobantes.
* **Brevo (anteriormente Sendinblue):** Proveedor de correo transaccional para el envío seguro de enlaces de recuperación de contraseña.
* **Google Secret Manager:** Almacenamiento seguro de secretos productivos (claves de base de datos, API keys, secretos JWT y credenciales S3).
* **Google Artifact Registry & Cloud Build:** Registro privado de imágenes Docker y compilación remota de contenedores.

---

## Flujo de Despliegue y Estrategia de Ramas (CI/CD)

El repositorio está configurado como un **monorepo** que alberga tanto el frontend como el backend. Las actualizaciones se automatizan mediante **GitHub Actions**.

### Convención de Ramas
* **`main` (Producción):**
  * Representa el código en vivo.
  * Cada `push` o `merge` a esta rama dispara los flujos de integración y despliegue continuo hacia producción.
  * Los **`hotfix/*`** nacen de `main`, se corrigen rápidamente, se mergean a `main` y luego se propagan a `develop`.
* **`develop` (Desarrollo):**
  * Rama base para el desarrollo del día a día.
  * Las nuevas funcionalidades (`feature/*`) y correcciones normales (`fix/*`) se originan y se integran en esta rama.
  * Cuando se decide lanzar una nueva versión, se crea un Pull Request de `develop` hacia `main`.

### Flujos de Trabajo en GitHub Actions
1. **Despliegue de Backend ([`deploy-backend.yml`](.github/workflows/deploy-backend.yml)):**
   * Se ejecuta automáticamente cuando hay cambios dentro de `backend/**` en la rama `main`.
   * Se autentica en Google Cloud con una cuenta de servicio dedicada (`GCP_SA_KEY`).
   * Envía el código a Google Cloud Build, compila la imagen en Artifact Registry y despliega una nueva revisión en Cloud Run inyectando los secretos de Secret Manager.
2. **Despliegue de Frontend ([`deploy-frontend.yml`](.github/workflows/deploy-frontend.yml)):**
   * Se ejecuta automáticamente cuando hay cambios dentro de `frontend/**` en la rama `main`.
   * Instala dependencias con Node.js 20, genera el build estático con Vite (`dist/`) y lo publica de inmediato en Firebase Hosting.

---

## Estructura del Repositorio

| Carpeta | Descripción |
|---|---|
| [`backend/`](backend/README.md) | Código fuente de la API REST, configuración de Flyway, Dockerfile y tests de integración. |
| [`frontend/`](frontend/README.md) | Aplicación SPA en React con Vite, Tailwind CSS y componentes visuales. |
| [`docs/`](docs/README.md) | Documentación técnica, arquitectura, estrategia de nube y [referencia de la API](docs/api_reference.md). |
| [`.github/workflows/`](.github/workflows/) | Definiciones YAML de CI/CD para GitHub Actions. |
| `saver_postman_collection.json` | Colección completa de Postman v2.1.0 para probar todos los endpoints de la API. |

---

## Inicio Rápido en Local

### Requisitos
* JDK 21
* Maven 3.9+
* Node.js 20+
* Docker Desktop

```bash
# 1. Configurar variables locales del backend
cp backend/.env.example backend/.env

# 2. Iniciar base de datos y storage local con Docker
cd backend
docker compose up -d

# 3. Iniciar la API de Spring Boot
mvn spring-boot:run

# 4. En otra terminal, iniciar el Frontend
cd ../frontend
npm install
npm run dev
```

* **Frontend:** `http://localhost:5173`
* **API Backend:** `http://localhost:8080` (Documentación Swagger UI: `http://localhost:8080/swagger-ui.html`)
* **Infraestructura local Docker:** PostgreSQL (`:5433`), SeaweedFS S3 (`:8333`), Mailpit (`:8025`).
