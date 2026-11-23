-- Preferencias por usuario; la entrega de cada canal se activa por etapas.
CREATE TABLE notification_preferences (
    id            UUID PRIMARY KEY,
    user_id       UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    kind          VARCHAR(32) NOT NULL,
    enabled       BOOLEAN NOT NULL DEFAULT FALSE,
    email_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    push_enabled  BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT uq_notification_preferences_user_kind UNIQUE (user_id, kind),
    CONSTRAINT chk_notification_preferences_kind CHECK (kind IN ('DUE_SOON', 'DUE_TODAY', 'OVERDUE'))
);
CREATE INDEX ix_notification_preferences_user ON notification_preferences (user_id);
