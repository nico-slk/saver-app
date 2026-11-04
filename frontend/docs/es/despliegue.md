# Despliegue

[English](../en/deployment.md) · [← Volver](../../README.md)

El frontend es una SPA estática que se publica en **Firebase Hosting** (proyecto de GCP).

## Por qué Firebase Hosting

`firebase.json` reescribe `/api/**` hacia el servicio de Cloud Run `saver-backend`. Así frontend y API comparten dominio:

- no hace falta CORS en producción;
- la cookie de sesión `SameSite=Strict` funciona;
- la cookie se llama `__session` porque **es la única que Firebase Hosting reenvía a Cloud Run**.

```json
"rewrites": [
  { "source": "/api/**", "run": { "serviceId": "saver-backend", "region": "southamerica-east1" } },
  { "source": "**", "destination": "/index.html" }
]
```

Si el servicio o la región del backend cambian, actualizar `firebase.json`.

## Primera vez

```bash
npm install -g firebase-tools
firebase login
firebase use --add            # elegir el proyecto de GCP
```

## Publicar

```bash
npm ci
npm run build
firebase deploy --only hosting
```

## Variables de entorno

| Variable | Uso |
|---|---|
| `VITE_API_URL` | URL base de la API. **Dejar vacía** con Firebase Hosting (mismo dominio). Solo se usa si la API está en otro dominio, en cuyo caso hay que configurar CORS y `SameSite=None` en el backend. |

Las variables `VITE_*` se incrustan en el build: **nunca poner secretos**.

## Cache

- `/assets/**` (archivos con hash): cache de 1 año, inmutable.
- `/index.html`: `no-cache`, para que cada deploy se vea al instante.

## Google Sign-In

Agregar el dominio de producción (ej. `https://saver.web.app`) en *Orígenes autorizados de JavaScript* del Client ID de Google. El frontend obtiene el Client ID del backend (`/api/auth/providers`), no hace falta configurarlo acá.

## CI/CD (sugerido)

GitHub Actions: `npm ci && npm run build && npm test` en cada PR; en `main`, deploy con `FirebaseExtended/action-hosting-deploy` (también genera URLs de vista previa por PR).
