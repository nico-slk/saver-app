-- Usuarios y autenticación.
-- Convenciones: UUID como PK, timestamps en timestamptz (UTC), montos futuros en NUMERIC(15,2).

CREATE TABLE users (
    id              UUID         PRIMARY KEY,
    email           VARCHAR(320) NOT NULL,
    password_hash   VARCHAR(100),
    google_sub      VARCHAR(255),
    display_name    VARCHAR(120) NOT NULL,
    locale          VARCHAR(10)  NOT NULL DEFAULT 'es',
    base_currency   VARCHAR(3)   NOT NULL DEFAULT 'ARS',
    timezone        VARCHAR(64)  NOT NULL DEFAULT 'America/Argentina/Buenos_Aires',
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT chk_users_currency CHECK (base_currency IN ('ARS', 'USD')),
    CONSTRAINT chk_users_auth_method CHECK (password_hash IS NOT NULL OR google_sub IS NOT NULL)
);

CREATE UNIQUE INDEX ux_users_email ON users (lower(email));
CREATE UNIQUE INDEX ux_users_google_sub ON users (google_sub) WHERE google_sub IS NOT NULL;

-- Refresh tokens opacos: se guarda solo el hash SHA-256; rotación en cada uso.
CREATE TABLE refresh_tokens (
    id          UUID         PRIMARY KEY,
    user_id     UUID         NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    token_hash  VARCHAR(64)  NOT NULL,
    expires_at  TIMESTAMPTZ  NOT NULL,
    revoked_at  TIMESTAMPTZ,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    user_agent  VARCHAR(255)
);

CREATE UNIQUE INDEX ux_refresh_tokens_hash ON refresh_tokens (token_hash);
CREATE INDEX ix_refresh_tokens_user ON refresh_tokens (user_id);
