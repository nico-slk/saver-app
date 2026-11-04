-- Inflación mensual oficial por moneda (ARS: INDEC vía ArgentinaDatos; USD: IPC de EE.UU. vía BLS).
-- rate es una fracción: 0.019 = 1,9 % mensual.
CREATE TABLE inflation_rates (
    currency   VARCHAR(3)    NOT NULL,
    month      DATE          NOT NULL,
    rate       NUMERIC(9, 6) NOT NULL,
    source     VARCHAR(30)   NOT NULL,
    fetched_at TIMESTAMPTZ   NOT NULL,
    PRIMARY KEY (currency, month),
    CONSTRAINT inflation_rates_month_first_day CHECK (EXTRACT(DAY FROM month) = 1),
    CONSTRAINT inflation_rates_rate_range CHECK (rate > -1 AND rate < 10)
);

-- Inflación mensual manual del usuario por moneda: reemplaza la estimación para los meses sin dato oficial.
CREATE TABLE user_inflation (
    user_id      UUID          NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    currency     VARCHAR(3)    NOT NULL,
    monthly_rate NUMERIC(7, 5) NOT NULL CHECK (monthly_rate >= 0 AND monthly_rate <= 0.5),
    updated_at   TIMESTAMPTZ   NOT NULL,
    PRIMARY KEY (user_id, currency)
);

-- La preferencia única de V3 pasa a ser la inflación manual en pesos.
INSERT INTO user_inflation (user_id, currency, monthly_rate, updated_at)
SELECT id, 'ARS', monthly_inflation, now() FROM users WHERE monthly_inflation IS NOT NULL;

ALTER TABLE users DROP COLUMN monthly_inflation;

-- Ingresos recurrentes con regla de aumento (igual que los gastos fijos).
ALTER TABLE incomes
    ADD COLUMN adjust_every_months   SMALLINT CHECK (adjust_every_months BETWEEN 1 AND 120),
    ADD COLUMN adjust_percent        NUMERIC(7, 4) CHECK (adjust_percent >= 0),
    ADD COLUMN adjust_with_inflation BOOLEAN NOT NULL DEFAULT FALSE;
