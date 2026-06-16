-- =============================================================================
-- Seed data for local development (profile: local-rich)
-- =============================================================================

CREATE TABLE IF NOT EXISTS users (
    username        VARCHAR(255) PRIMARY KEY,
    hashed_password VARCHAR(255) NOT NULL
);

CREATE TABLE IF NOT EXISTS client_profiles (
    card_token           VARCHAR(255) PRIMARY KEY,
    credit_limit         NUMERIC(19, 4) NOT NULL,
    used_credit          NUMERIC(19, 4) NOT NULL,
    max_installments     INTEGER        NOT NULL,
    monthly_rate         NUMERIC(5, 4)  NOT NULL,
    vip                  BOOLEAN        NOT NULL DEFAULT FALSE,
    home_location_code   VARCHAR(100)
);

CREATE TABLE IF NOT EXISTS card_transactions (
    correlation_id   UUID           PRIMARY KEY,
    transaction_id   VARCHAR(255)   NOT NULL,
    card_token       VARCHAR(255)   NOT NULL,
    amount           NUMERIC(19, 4) NOT NULL,
    installments     INTEGER        NOT NULL,
    brand            VARCHAR(50)    NOT NULL,
    status           VARCHAR(50)    NOT NULL,
    created_at       TIMESTAMP      NOT NULL,
    callback_url     VARCHAR(2048),
    reason           VARCHAR(1024),
    location_code    VARCHAR(100)
);

-- Users ------------------------------------------------------------------
INSERT INTO users (username, hashed_password) VALUES
    ('usuario_teste', '$2b$10$C6VMwOEfFDbpeaOtKS/gvu/.Ndn4.aN2LD5pqvJyvGBNVsCsCaIQW'),
    ('admin',         '$2b$10$cL3qgYMJ5zAVPdsIAHNpVuy2eTyzjOcUjAKqbXfBsopJzLrCvo3om')
ON CONFLICT (username) DO NOTHING;

-- Scenario 1: CAMINHO FELIZ (VIP) -----------------------------------------
--   Card:   4111 1111 1111 1111 (VISA)
--   Token:  9bbef194-7662-3ca5-6c17-da75fd57734d
--   Home:   SAO_PAULO_CENTRO
INSERT INTO client_profiles (card_token, credit_limit, used_credit, max_installments, monthly_rate, vip, home_location_code) VALUES
    ('9bbef194-7662-3ca5-6c17-da75fd57734d', 10000.0000, 0.0000, 12, 0.0199, true, 'SAO_PAULO_CENTRO')
ON CONFLICT (card_token) DO UPDATE
    SET credit_limit       = EXCLUDED.credit_limit,
        used_credit        = EXCLUDED.used_credit,
        vip                = EXCLUDED.vip,
        home_location_code = EXCLUDED.home_location_code;

-- Scenario 2: TRANSAÇÃO NEGADA (crédito insuficiente) ---------------------
--   Card:   5500 0055 5555 5559 (MASTERCARD)
--   Token:  db3912d7-7ffa-433c-9629-aea6151a98b3
INSERT INTO client_profiles (card_token, credit_limit, used_credit, max_installments, monthly_rate, vip, home_location_code) VALUES
    ('db3912d7-7ffa-433c-9629-aea6151a98b3', 500.0000, 490.0000, 6, 0.0199, false, 'SAO_PAULO_CENTRO')
ON CONFLICT (card_token) DO UPDATE
    SET credit_limit       = EXCLUDED.credit_limit,
        used_credit        = EXCLUDED.used_credit,
        vip                = EXCLUDED.vip,
        home_location_code = EXCLUDED.home_location_code;

-- Scenario 3: BLOQUEADA PELO ANTI-FRAUDE ----------------------------------
--   Card:   4000 0000 0000 0002 (VISA)
--   Token:  acd08f29-a41f-2e55-ab0c-4f774b1562b0
--   Redis:  FRAUD:acd08f29-... = {"score":95}
INSERT INTO client_profiles (card_token, credit_limit, used_credit, max_installments, monthly_rate, vip, home_location_code) VALUES
    ('acd08f29-a41f-2e55-ab0c-4f774b1562b0', 10000.0000, 0.0000, 12, 0.0199, false, 'SAO_PAULO_CENTRO')
ON CONFLICT (card_token) DO UPDATE
    SET credit_limit       = EXCLUDED.credit_limit,
        used_credit        = EXCLUDED.used_credit,
        vip                = EXCLUDED.vip,
        home_location_code = EXCLUDED.home_location_code;
