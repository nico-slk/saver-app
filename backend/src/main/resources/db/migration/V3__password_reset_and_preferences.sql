-- Recuperación de contraseña por email y preferencia de inflación para proyecciones.

-- Token de un solo uso enviado por email. Se guarda solo el hash SHA-256.
CREATE TABLE password_reset_tokens (
    id          UUID         PRIMARY KEY,
    user_id     UUID         NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    token_hash  VARCHAR(64)  NOT NULL,
    expires_at  TIMESTAMPTZ  NOT NULL,
    used_at     TIMESTAMPTZ,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX ux_password_reset_tokens_hash ON password_reset_tokens (token_hash);
CREATE INDEX ix_password_reset_tokens_user ON password_reset_tokens (user_id, created_at);

-- Inflación mensual estimada (fracción: 0.025 = 2,5 %) usada en proyecciones y en aumentos atados a inflación.
ALTER TABLE users ADD COLUMN monthly_inflation NUMERIC(7, 5);
ALTER TABLE users ADD CONSTRAINT chk_users_inflation CHECK (monthly_inflation IS NULL OR (monthly_inflation >= 0 AND monthly_inflation <= 1));
