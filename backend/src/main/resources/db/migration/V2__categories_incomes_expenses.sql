-- Etapa 2: categorías, ingresos, gastos (plantillas) y ocurrencias mensuales.
-- Los meses se guardan como DATE del día 1. Montos NUMERIC(15,2).

-- Categorías: las de sistema tienen user_id NULL y system_key (el front traduce el nombre).
-- Borrado lógico (deleted_at) para conservar el historial.
CREATE TABLE categories (
    id          UUID         PRIMARY KEY,
    user_id     UUID         REFERENCES users (id) ON DELETE CASCADE,
    system_key  VARCHAR(30),
    name        VARCHAR(60),
    color       VARCHAR(7)   NOT NULL,
    icon        VARCHAR(30),
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    deleted_at  TIMESTAMPTZ,
    CONSTRAINT chk_categories_owner CHECK (
        (user_id IS NULL AND system_key IS NOT NULL) OR (user_id IS NOT NULL AND name IS NOT NULL))
);

CREATE UNIQUE INDEX ux_categories_system_key ON categories (system_key) WHERE system_key IS NOT NULL;
CREATE UNIQUE INDEX ux_categories_user_name ON categories (user_id, lower(name))
    WHERE user_id IS NOT NULL AND deleted_at IS NULL;
CREATE INDEX ix_categories_user ON categories (user_id);

INSERT INTO categories (id, system_key, color, icon) VALUES
    ('00000000-0000-0000-0000-000000000001', 'rent',         '#6366f1', 'home'),
    ('00000000-0000-0000-0000-000000000002', 'utilities',    '#0ea5e9', 'bolt'),
    ('00000000-0000-0000-0000-000000000003', 'installments', '#f59e0b', 'card'),
    ('00000000-0000-0000-0000-000000000004', 'other',        '#64748b', 'dots');

-- Ingresos. FIXED = sueldo (indefinido). EXTRA = puntual (start = end) o recurrente (N meses o indefinido).
CREATE TABLE incomes (
    id           UUID          PRIMARY KEY,
    user_id      UUID          NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    description  VARCHAR(120)  NOT NULL,
    kind         VARCHAR(10)   NOT NULL,
    currency     VARCHAR(3)    NOT NULL,
    start_month  DATE          NOT NULL,
    end_month    DATE,
    created_at   TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT chk_incomes_kind CHECK (kind IN ('FIXED', 'EXTRA')),
    CONSTRAINT chk_incomes_currency CHECK (currency IN ('ARS', 'USD')),
    CONSTRAINT chk_incomes_range CHECK (end_month IS NULL OR end_month >= start_month)
);
CREATE INDEX ix_incomes_user ON incomes (user_id);

-- Montos versionados por vigencia (aumentos de sueldo).
CREATE TABLE income_amounts (
    id               UUID           PRIMARY KEY,
    income_id        UUID           NOT NULL REFERENCES incomes (id) ON DELETE CASCADE,
    effective_month  DATE           NOT NULL,
    amount           NUMERIC(15, 2) NOT NULL,
    CONSTRAINT chk_income_amounts_positive CHECK (amount > 0),
    CONSTRAINT ux_income_amounts UNIQUE (income_id, effective_month)
);

-- Plantillas de gasto.
-- ONE_TIME: un mes. INSTALLMENTS: N cuotas desde start_month. RECURRING: indefinido (end_month al darlo de baja).
CREATE TABLE expenses (
    id                     UUID          PRIMARY KEY,
    user_id                UUID          NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    category_id            UUID          NOT NULL REFERENCES categories (id),
    description            VARCHAR(120)  NOT NULL,
    kind                   VARCHAR(12)   NOT NULL,
    currency               VARCHAR(3)    NOT NULL,
    start_month            DATE          NOT NULL,
    end_month              DATE,
    installments           INTEGER,
    due_day                SMALLINT,
    variable_amount        BOOLEAN       NOT NULL DEFAULT FALSE,
    adjust_every_months    SMALLINT,
    adjust_percent         NUMERIC(7, 4),
    adjust_with_inflation  BOOLEAN       NOT NULL DEFAULT FALSE,
    notes                  VARCHAR(500),
    created_at             TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at             TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT chk_expenses_kind CHECK (kind IN ('ONE_TIME', 'INSTALLMENTS', 'RECURRING')),
    CONSTRAINT chk_expenses_currency CHECK (currency IN ('ARS', 'USD')),
    CONSTRAINT chk_expenses_range CHECK (end_month IS NULL OR end_month >= start_month),
    CONSTRAINT chk_expenses_installments CHECK (
        (kind = 'INSTALLMENTS' AND installments BETWEEN 1 AND 360) OR (kind <> 'INSTALLMENTS' AND installments IS NULL)),
    CONSTRAINT chk_expenses_due_day CHECK (due_day IS NULL OR due_day BETWEEN 1 AND 31),
    CONSTRAINT chk_expenses_adjust CHECK (adjust_every_months IS NULL OR adjust_every_months BETWEEN 1 AND 120)
);
CREATE INDEX ix_expenses_user ON expenses (user_id);

-- Montos versionados por vigencia (aumentos del alquiler, cambios de tarifa).
CREATE TABLE expense_amounts (
    id               UUID           PRIMARY KEY,
    expense_id       UUID           NOT NULL REFERENCES expenses (id) ON DELETE CASCADE,
    effective_month  DATE           NOT NULL,
    amount           NUMERIC(15, 2) NOT NULL,
    CONSTRAINT chk_expense_amounts_positive CHECK (amount > 0),
    CONSTRAINT ux_expense_amounts UNIQUE (expense_id, effective_month)
);

-- Lo que hay que pagar en cada mes. Se generan desde las plantillas hasta el mes actual.
CREATE TABLE expense_occurrences (
    id                  UUID           PRIMARY KEY,
    user_id             UUID           NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    expense_id          UUID           NOT NULL REFERENCES expenses (id) ON DELETE CASCADE,
    month               DATE           NOT NULL,
    installment_number  INTEGER,
    due_date            DATE           NOT NULL,
    expected_amount     NUMERIC(15, 2) NOT NULL,
    paid_amount         NUMERIC(15, 2) NOT NULL DEFAULT 0,
    currency            VARCHAR(3)     NOT NULL,
    status              VARCHAR(10)    NOT NULL DEFAULT 'PENDING',
    notes               VARCHAR(500),
    created_at          TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ    NOT NULL DEFAULT now(),
    CONSTRAINT chk_occurrences_status CHECK (status IN ('PENDING', 'PARTIAL', 'PAID')),
    CONSTRAINT ux_occurrences_expense_month UNIQUE (expense_id, month)
);
CREATE INDEX ix_occurrences_user_month ON expense_occurrences (user_id, month);
CREATE INDEX ix_occurrences_user_status ON expense_occurrences (user_id, status);
