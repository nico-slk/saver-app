# Authentication

[Español](../es/autenticacion.md) · [← Back](../../README.en.md)

## Tokens

| Token | Format | Lifetime | Where it lives |
|---|---|---|---|
| **Access token** | JWT HS256 (`sub` = user id, `email`, `iss` = `saver-api`) | 15 min | Browser memory (never localStorage) |
| **Refresh token** | 32 random bytes, base64url (opaque) | 30 days | Cookie `__session`: `HttpOnly`, `SameSite=Strict`, `Path=/api/auth`, `Secure` in prod |

Only the **SHA-256 hash** of the refresh token is stored in the database.

> The cookie is named `__session` because **Firebase Hosting only forwards that cookie** to Cloud Run; with any other name, refresh would not work in production.

## Flow

```
Login / Sign-up / Google
  └─▶ 200 { accessToken, expiresIn, user }  +  Set-Cookie: __session=…

API request
  └─▶ Authorization: Bearer <accessToken>

Expired access token (401)
  └─▶ POST /api/auth/refresh (sends the cookie)
        ├─ valid   → revokes the used token, issues a new one (rotation) + new access token
        ├─ revoked < 10 s ago → 401 (two tabs refreshing at once, not theft)
        ├─ revoked earlier → possible theft: revokes ALL user sessions → 401
        └─ expired/unknown → 401 and the cookie is cleared

Logout
  └─▶ POST /api/auth/logout → revokes the refresh token and clears the cookie
```

- On `/api/auth/**` the `Authorization` header is **ignored**, so an expired access token never blocks `/refresh` or `/logout`.
- The frontend serializes refreshes across tabs using the Web Locks API.
- Since the cookie is `SameSite=Strict` and only sent to `/api/auth`, CSRF does not apply to the rest of the API (which uses a Bearer header).
- For `SameSite=Strict` to work in production, frontend and API must share the site: Firebase Hosting rewrites `/api/**` to Cloud Run (see [Deployment](deployment.md)).

## Passwords

- BCrypt with cost 12.
- Between 8 characters and **72 bytes** (BCrypt limit; with accents or ñ, 72 characters can exceed it).
- If the email does not exist or the password does not match, the same error is always returned (`auth.invalid_credentials`) to avoid revealing which accounts exist.

## Google

1. The frontend shows the Google Identity Services button and obtains an **ID token**.
2. `POST /api/auth/google { idToken, locale }`.
3. The backend validates signature (Google JWKS), expiry, issuer (`accounts.google.com`) and audience (`GOOGLE_CLIENT_ID`), and requires `email_verified`.
4. It looks up the user by `google_sub`; if not found, by email (and **links** the account); otherwise it creates the user.

## Current user in controllers

```java
@GetMapping
public Something list(@CurrentUser UUID userId) { … }
```

`@CurrentUser` reads the JWT `sub`. Every repository must filter by that id.
