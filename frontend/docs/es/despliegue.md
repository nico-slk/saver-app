# Despliegue

[English](../en/deployment.md) · [← Volver](../../README.md)

El frontend es una SPA estática que se publica en **Firebase Hosting**. La configuración actual apunta al proyecto `portfolio-slk2023` (plan Blaze).

## Hosting con API en Cloud Run

`firebase.json` publica `dist`, reenvía `/api/**` al servicio `saver-backend` en `southamerica-east1` y reescribe el resto de las rutas de la SPA a `/index.html`. Firebase Hosting y Cloud Run deben pertenecer al mismo proyecto. El proyecto debe tener Blaze activo para esta integración.

El rewrite mantiene el mismo origen entre la aplicación y la API. Así funciona la cookie de refresh `SameSite=Strict`; Firebase Hosting reenvía a Cloud Run las cookies con nombre `__session`.

No hace falta agregar Firebase JS SDK para publicar el sitio. Saver no usa Firebase Analytics ni Firebase Authentication: el login y la sesión pertenecen al backend propio.

## Primera vez

```bash
npm install -g firebase-tools
firebase login
firebase projects:list        # confirmar acceso al proyecto portfolio-slk2023
firebase hosting:sites:list   # comprobar si ya existe un sitio
firebase hosting:sites:create portfolio-slk2023  # solo si todavía no hay un sitio
```

Desde el 15 de octubre de 2026 los proyectos nuevos no reciben automáticamente un sitio Hosting. El primer sitio creado es el predeterminado. Confirmá el dominio asignado antes de completar `APP_WEB_URL`, CORS y los orígenes OAuth. El SDK web no crea por sí solo un sitio Hosting.

## Publicar

```bash
npm ci
npm run build
firebase deploy --only hosting
```

Desplegar primero el servicio Cloud Run `saver-backend`; la regla `/api/**` necesita que exista en el proyecto y región configurados.

## Variables de entorno

| Variable | Uso |
|---|---|
| `VITE_API_URL` | Vacía para que el navegador use `/api` en el mismo origen. Solo definirla si se cambia a un backend externo y se configura CORS/cookies. |

Las variables `VITE_*` se incrustan en el build: **nunca poner secretos**.

## Cache

- `/assets/**` (archivos con hash): cache de 1 año, inmutable.
- `/index.html`: `no-cache`, para que cada deploy se vea al instante.

## Google Sign-In

Si se habilita Google Sign-In, agregar el dominio real de Firebase Hosting (por ejemplo, `https://portfolio-slk2023.web.app`) en *Orígenes autorizados de JavaScript* del Client ID de Google. Verificar el dominio con `firebase hosting:sites:list`; el ID del sitio y el dominio asignado pueden diferir. El frontend obtiene el Client ID del backend (`/api/auth/providers`).

## CI/CD (sugerido)

GitHub Actions: `npm ci && npm run build && npm test` en cada PR; en `main`, deploy con `FirebaseExtended/action-hosting-deploy` (también genera URLs de vista previa por PR).
