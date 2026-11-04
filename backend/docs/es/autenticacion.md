# Autenticación

[English](../en/authentication.md) · [← Volver](../../README.md)

## Tokens

| Token | Formato | Vida | Dónde vive |
|---|---|---|---|
| **Access token** | JWT HS256 (`sub` = id de usuario, `email`, `iss` = `saver-api`) | 15 min | Memoria del navegador (nunca en localStorage) |
| **Refresh token** | 32 bytes aleatorios en base64url (opaco) | 30 días | Cookie `__session`: `HttpOnly`, `SameSite=Strict`, `Path=/api/auth`, `Secure` en prod |

En la base solo se guarda el **hash SHA-256** del refresh token.

> La cookie se llama `__session` porque **Firebase Hosting solo reenvía esa cookie** a Cloud Run; con cualquier otro nombre el refresh no funcionaría en producción.

## Flujo

```
Login / Registro / Google
  └─▶ 200 { accessToken, expiresIn, user }  +  Set-Cookie: __session=…

Request a la API
  └─▶ Authorization: Bearer <accessToken>

Access token vencido (401)
  └─▶ POST /api/auth/refresh (envía la cookie)
        ├─ válido   → revoca el token usado, emite uno nuevo (rotación) + nuevo access token
        ├─ revocado hace < 10 s → 401 (dos pestañas refrescando a la vez, no es robo)
        ├─ revocado hace más → posible robo: revoca TODAS las sesiones del usuario → 401
        └─ vencido/inexistente → 401 y se borra la cookie

Logout
  └─▶ POST /api/auth/logout → revoca el refresh token y borra la cookie
```

- En `/api/auth/**` el header `Authorization` se **ignora**, para que un access token vencido no bloquee `/refresh` ni `/logout`.
- El frontend serializa los refresh entre pestañas con la Web Locks API.
- Como la cookie es `SameSite=Strict` y solo viaja a `/api/auth`, CSRF no aplica al resto de la API (que usa header Bearer).
- Para que `SameSite=Strict` funcione en producción, el frontend y la API deben compartir el sitio: Firebase Hosting reescribe `/api/**` hacia Cloud Run (ver [Despliegue](despliegue.md)).

## Contraseñas

- BCrypt con costo 12.
- Entre 8 caracteres y **72 bytes** (límite de BCrypt; con acentos o ñ, 72 caracteres pueden superar ese límite).
- Si el email no existe o la contraseña no coincide, se responde siempre el mismo error (`auth.invalid_credentials`) para no revelar qué cuentas existen.

## Google

1. El frontend muestra el botón de Google Identity Services y obtiene un **ID token**.
2. `POST /api/auth/google { idToken, locale }`.
3. El backend valida firma (JWKS de Google), expiración, emisor (`accounts.google.com`) y audiencia (`GOOGLE_CLIENT_ID`), y exige `email_verified`.
4. Busca el usuario por `google_sub`; si no existe, por email (y **vincula** la cuenta); si tampoco, lo crea.

## Usuario actual en los controllers

```java
@GetMapping
public Algo listar(@CurrentUser UUID userId) { … }
```

`@CurrentUser` toma el `sub` del JWT. Todo repositorio debe filtrar por ese id.
