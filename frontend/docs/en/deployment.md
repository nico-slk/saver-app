# Deployment

[Español](../es/despliegue.md) · [← Back](../../README.en.md)

The frontend is a static SPA published on **Firebase Hosting** (GCP project).

## Why Firebase Hosting

`firebase.json` rewrites `/api/**` to the `saver-backend` Cloud Run service. This way frontend and API share the same domain:

- no CORS needed in production;
- the `SameSite=Strict` session cookie works;
- the cookie is named `__session` because **it is the only cookie Firebase Hosting forwards to Cloud Run**.

```json
"rewrites": [
  { "source": "/api/**", "run": { "serviceId": "saver-backend", "region": "southamerica-east1" } },
  { "source": "**", "destination": "/index.html" }
]
```

If the backend service name or region change, update `firebase.json`.

## First time

```bash
npm install -g firebase-tools
firebase login
firebase use --add            # pick the GCP project
```

## Publish

```bash
npm ci
npm run build
firebase deploy --only hosting
```

## Environment variables

| Variable | Usage |
|---|---|
| `VITE_API_URL` | API base URL. **Leave empty** with Firebase Hosting (same domain). Only used if the API is on another domain, in which case CORS and `SameSite=None` must be configured in the backend. |

`VITE_*` variables are embedded in the build: **never put secrets in them**.

## Caching

- `/assets/**` (hashed files): 1-year immutable cache.
- `/index.html`: `no-cache`, so every deploy is visible immediately.

## Google Sign-In

Add the production domain (e.g. `https://saver.web.app`) to the *Authorized JavaScript origins* of the Google Client ID. The frontend gets the Client ID from the backend (`/api/auth/providers`); nothing to configure here.

## CI/CD (suggested)

GitHub Actions: `npm ci && npm run build && npm test` on every PR; on `main`, deploy with `FirebaseExtended/action-hosting-deploy` (it also creates preview URLs per PR).
