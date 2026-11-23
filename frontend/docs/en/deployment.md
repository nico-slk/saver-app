# Deployment

[Español](../es/despliegue.md) · [← Back](../../README.en.md)

The frontend is a static SPA published on **Firebase Hosting**. The current configuration targets project `portfolio-slk2023` (Blaze plan).

## Hosting with API on Cloud Run

`firebase.json` publishes `dist`, forwards `/api/**` to the `saver-backend` service in `southamerica-east1`, and rewrites remaining SPA routes to `/index.html`. Firebase Hosting and Cloud Run must belong to the same project. Blaze must be enabled for this integration.

The rewrite keeps the app and API same-origin. This allows the `SameSite=Strict` refresh cookie to work; Firebase Hosting forwards cookies named `__session` to Cloud Run.

The Firebase JS SDK is not needed to publish the site. Saver does not use Firebase Analytics or Firebase Authentication; sign-in and sessions belong to the custom backend.

## First time

```bash
npm install -g firebase-tools
firebase login
firebase projects:list        # confirm access to project portfolio-slk2023
firebase hosting:sites:list   # check whether a site already exists
firebase hosting:sites:create portfolio-slk2023  # only if no site exists yet
```

Since October 15, 2026, new projects do not get a Hosting site automatically. The first created site becomes the default. Confirm its assigned domain before setting `APP_WEB_URL`, CORS, and OAuth origins. The Web SDK does not create a Hosting site by itself.

## Publish

```bash
npm ci
npm run build
firebase deploy --only hosting
```

Deploy the `saver-backend` Cloud Run service first; the `/api/**` rewrite needs that service to exist in the configured project and region.

## Environment variables

| Variable | Usage |
|---|---|
| `VITE_API_URL` | Leave empty so the browser uses same-origin `/api`. Set it only if switching to an external backend and configuring CORS/cookies. |

`VITE_*` variables are embedded in the build: **never put secrets in them**.

## Caching

- `/assets/**` (hashed files): 1-year immutable cache.
- `/index.html`: `no-cache`, so every deploy is visible immediately.

## Google Sign-In

If Google Sign-In is enabled, add the actual Firebase Hosting domain (for example, `https://portfolio-slk2023.web.app`) to the Google Client ID's *Authorized JavaScript origins*. Verify the domain with `firebase hosting:sites:list`; the site ID and assigned domain may differ. The frontend gets the Client ID from the backend (`/api/auth/providers`).

## CI/CD (suggested)

GitHub Actions: `npm ci && npm run build && npm test` on every PR; on `main`, deploy with `FirebaseExtended/action-hosting-deploy` (it also creates preview URLs per PR).
