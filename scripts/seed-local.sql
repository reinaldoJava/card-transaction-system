-- =============================================================================
-- Seed data for local development (profile: local-rich)
-- Executado automaticamente pelo Postgres na primeira inicialização do volume
-- (montado em /docker-entrypoint-initdb.d via docker-compose).
--
-- Inclui DDL (CREATE TABLE IF NOT EXISTS) porque o script roda antes do
-- Spring Boot. Com ddl-auto: update, o Hibernate valida/complementa o schema
-- existente sem conflito.
-- =============================================================================

-- Schema -----------------------------------------------------------------

CREATE TABLE IF NOT EXISTS users (
    username        VARCHAR(255) PRIMARY KEY,
    hashed_password VARCHAR(255) NOT NULL
);

CREATE TABLE IF NOT EXISTS client_profiles (
    card_token        VARCHAR(255) PRIMARY KEY,
    credit_limit      NUMERIC(19, 4) NOT NULL,
    used_credit       NUMERIC(19, 4) NOT NULL,
    max_installments  INTEGER        NOT NULL,
    monthly_rate      NUMERIC(5, 4)  NOT NULL,
    vip               BOOLEAN        NOT NULL DEFAULT FALSE
);

-- Users ------------------------------------------------------------------
-- Senhas: usuario_teste=senha123 · admin=admin123

INSERT INTO users (username, hashed_password) VALUES
    ('usuario_teste', '$2b$10$C6VMwOEfFDbpeaOtKS/gvu/.Ndn4.aN2LD5pqvJyvGBNVsCsCaIQW'),
    ('admin',         '$2b$10$cL3qgYMJ5zAVPdsIAHNpVuy2eTyzjOcUjAKqbXfBsopJzLrCvo3om')
ON CONFLICT (username) DO NOTHING;

-- Scenario 1: CAMINHO FELIZ (VIP) -----------------------------------------
--   Card:   4111 1111 1111 1111 (VISA)
--   Token:  9bbef194-7662-3ca5-6c17-da75fd57734d
--   Limite: R$ 10.000 / usado R$ 0 / max 12x / taxa 1,99%/mês / VIP = true
--   Teste:  R$ 500 / 3x  →  APPROVED

INSERT INTO client_profiles (card_token, credit_limit, used_credit, max_installments, monthly_rate, vip) VALUES
    ('9bbef194-7662-3ca5-6c17-da75fd57734d', 10000.0000, 0.0000, 12, 0.0199, true)
ON CONFLICT (card_token) DO UPDATE
    SET credit_limit = EXCLUDED.credit_limit,
        used_credit  = EXCLUDED.used_credit,
        vip          = EXCLUDED.vip;

-- Scenario 2: TRANSAÇÃO NEGADA (crédito insuficiente) ---------------------
--   Card:   5500 0055 5555 5559 (MASTERCARD)
--   Token:  db3912d7-7ffa-433c-9629-aea6151a98b3
--   Limite: R$ 500 / usado R$ 490 / disponível R$ 10 / VIP = false
--   Teste:  R$ 200 / 1x  →  REJECTED (total c/ juros R$ 203,98 > R$ 10,00)

INSERT INTO client_profiles (card_token, credit_limit, used_credit, max_installments, monthly_rate, vip) VALUES
    ('db3912d7-7ffa-433c-9629-aea6151a98b3', 500.0000, 490.0000, 6, 0.0199, false)
ON CONFLICT (card_token) DO UPDATE
    SET credit_limit = EXCLUDED.credit_limit,
        used_credit  = EXCLUDED.used_credit,
        vip          = EXCLUDED.vip;

-- Scenario 3: BLOQUEADA PELO ANTI-FRAUDE ----------------------------------
--   Card:   4000 0000 0000 0002 (VISA)
--   Token:  acd08f29-a41f-2e55-ab0c-4f774b1562b0
--   Limite: R$ 10.000 / usado R$ 0  (crédito OK — rejeição pelo score Redis)
--   Redis:  FRAUD:acd08f29-a41f-2e55-ab0c-4f774b1562b0 = {"score":95}
--           → seedado automaticamente pelo serviço redis-seed no docker-compose
--   Teste:  qualquer valor  →  REJECTED (score 95 >= threshold 80)

INSERT INTO client_profiles (card_token, credit_limit, used_credit, max_installments, monthly_rate, vip) VALUES
    ('acd08f2