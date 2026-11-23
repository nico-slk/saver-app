-- Etapa 3A: cuentas, tarjetas, cotizaciones, pagos con comprobantes, cobros, transferencias y ajustes.

-- Cuentas donde está la plata. SAVINGS e INVESTMENT cuentan como ahorro.
CREATE TABLE accounts (
    id              UUID           PRIMARY KEY,
    user_id         UUID           NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    name            VARCHAR(60)    NOT NULL,
    kind            VARCHAR(12)    NOT NULL,
    currency        VARCHAR(3)     NOT NULL,
    -- Saldo al día opening_date: los movimientos anteriores a esa fecha no lo modifican.
    opening_balance NUMERIC(15, 2) NOT NULL DEFAULT 0,
    opening_date    DATE           NOT NULL,
    color           VARCHAR(7)     NOT NULL,
    archived_at     TIMESTAMPTZ,
    created_at      TIMESTAMPTZ    NOT NULL,
    updated_at      TIMESTAMPTZ    NOT NULL,
    CONSTRAINT chk_accounts_kind CHECK (kind IN ('CHECKING', 'CASH', 'WALLET', 'SAVINGS', 'INVESTMENT')),
    CONSTRAINT chk_accounts_currency CHECK (currency IN ('ARS', 'USD'))
);
CREATE INDEX ix_accounts_user ON accounts (user_id);

-- Tarjetas: solo agrupan gastos (sin resumen ni cierre).
CREATE TABLE cards (
    id          UUID        PRIMARY KEY,
    user_id     UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    name        VARCHAR(60) NOT NULL,
    color       VARCHAR(7)  NOT NULL,
    archived_at TIMESTAMPTZ,
    created_at  TIMESTAMPTZ NOT NULL
);
CREATE INDEX ix_cards_user ON cards (user_id);

ALTER TABLE expenses ADD COLUMN card_id UUID REFERENCES cards (id) ON DELETE SET NULL;

-- Cotizaciones del dólar por día y tipo (pesos por dólar), de dolarapi.com o cargadas a mano.
CREATE TABLE exchange_rates (
    day        DATE           NOT NULL,
    type       VARCHAR(12)    NOT NULL,
    buy        NUMERIC(15, 4),
    sell       NUMERIC(15, 4) NOT NULL,
    source     VARCHAR(30)    NOT NULL,
    fetched_at TIMESTAMPTZ    NOT NULL,
    PRIMARY KEY (day, type),
    CONSTRAINT chk_exchange_rates_type CHECK (type IN ('OFICIAL', 'MEP', 'BLUE', 'TARJETA', 'CCL', 'MAYORISTA', 'CRIPTO'))
);

-- Pagos de una ocurrencia. assumed = registrado al cargar un gasto con meses pasados (sin cuenta, no mueve saldos).
-- amount está en la moneda del gasto; account_amount en la de la cuenta (rate = pesos por dólar).
CREATE TABLE payments (
    id             UUID           PRIMARY KEY,
    user_id        UUID           NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    occurrence_id  UUID           NOT NULL REFERENCES expense_occurrences (id) ON DELETE CASCADE,
    account_id     UUID           REFERENCES accounts (id),
    paid_on        DATE           NOT NULL,
    amount         NUMERIC(15, 2) NOT NULL CHECK (amount > 0),
    account_amount NUMERIC(15, 2),
    rate           NUMERIC(15, 4),
    rate_type      VARCHAR(12),
    notes          VARCHAR(500),
    assumed        BOOLEAN        NOT NULL DEFAULT FALSE,
    created_at     TIMESTAMPTZ    NOT NULL,
    CONSTRAINT chk_payments_account CHECK (assumed OR (account_id IS NOT NULL AND account_amount IS NOT NULL))
);
CREATE INDEX ix_payments_occurrence ON payments (occurrence_id);
CREATE INDEX ix_payments_account ON payments (account_id, paid_on);

CREATE TABLE payment_attachments (
    id           UUID         PRIMARY KEY,
    user_id      UUID         NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    payment_id   UUID         NOT NULL REFERENCES payments (id) ON DELETE CASCADE,
    storage_key  VARCHAR(300) NOT NULL,
    file_name    VARCHAR(200) NOT NULL,
    content_type VARCHAR(100) NOT NULL,
    size_bytes   BIGINT       NOT NULL,
    created_at   TIMESTAMPTZ  NOT NULL
);
CREATE INDEX ix_payment_attachments_payment ON payment_attachments (payment_id);

-- Cobros de un ingreso en un mes (puede haber varios: cobros parciales).
CREATE TABLE income_receipts (
    id             UUID           PRIMARY KEY,
    user_id        UUID           NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    income_id      UUID           NOT NULL REFERENCES incomes (id),
    month          DATE           NOT NULL,
    account_id     UUID           NOT NULL REFERENCES accounts (id),
    received_on    DATE           NOT NULL,
    amount         NUMERIC(15, 2) NOT NULL CHECK (amount > 0),
    account_amount NUMERIC(15, 2) NOT NULL,
    rate           NUMERIC(15, 4),
    rate_type      VARCHAR(12),
    notes          VARCHAR(500),
    created_at     TIMESTAMPTZ    NOT NULL
);
CREATE INDEX ix_income_receipts_income ON income_receipts (income_id, month);
CREATE INDEX ix_income_receipts_account ON income_receipts (account_id, received_on);

-- Transferencias entre cuentas propias (incluye compra/venta de dólares).
CREATE TABLE transfers (
    id              UUID           PRIMARY KEY,
    user_id         UUID           NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    from_account_id UUID           NOT NULL REFERENCES accounts (id),
    to_account_id   UUID           NOT NULL REFERENCES accounts (id),
    transfer_on     DATE           NOT NULL,
    from_amount     NUMERIC(15, 2) NOT NULL CHECK (from_amount > 0),
    to_amount       NUMERIC(15, 2) NOT NULL CHECK (to_amount > 0),
    rate            NUMERIC(15, 4),
    rate_type       VARCHAR(12),
    notes           VARCHAR(500),
    created_at      TIMESTAMPTZ    NOT NULL,
    CONSTRAINT chk_transfers_accounts CHECK (from_account_id <> to_account_id)
);
CREATE INDEX ix_transfers_from ON transfers (from_account_id, transfer_on);
CREATE INDEX ix_transfers_to ON transfers (to_account_id, transfer_on);

-- Ajustes de saldo (diferencias al conciliar, intereses, etc.). amount con signo.
CREATE TABLE account_adjustments (
    id          UUID           PRIMARY KEY,
    user_id     UUID           NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    account_id  UUID           NOT NULL REFERENCES accounts (id),
    adjusted_on DATE           NOT NULL,
    amount      NUMERIC(15, 2) NOT NULL CHECK (amount <> 0),
    notes       VARCHAR(500),
    created_at  TIMESTAMPTZ    NOT NULL
);
CREATE INDEX ix_account_adjustments_account ON account_adjustments (account_id, adjusted_on);
