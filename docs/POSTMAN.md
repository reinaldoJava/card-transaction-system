# Postman — Card Transaction System

Arquivos em `scripts/`:

| Arquivo | Descrição |
|---|---|
| `card-transaction-system.postman_collection.json` | Collection principal com todos os cenários |
| `local-rich.postman_environment.json` | Environment para rodar local (docker-compose + Spring) |
| `aws.postman_environment.json` | Environment para rodar na AWS (Lambda Function URL) |

## Importação

1. Abra o Postman
2. **Import** → selecione os 3 arquivos simultaneamente
3. Selecione o environment **local-rich** ou **aws** no canto superior direito
4. Preencha a variável `password` no environment (secret)

## Pré-requisitos locais

```bash
docker-compose up -d
SPRING_PROFILES_ACTIVE=local-rich ./mvnw spring-boot:run
```

## Fluxo de uso

Execute sempre o **Auth / Login** primeiro. O `token` é salvo automaticamente.
Cada cenário tem dois requests: `Xa` (POST /process) e `Xb` (GET /status).
O `correlationId` é propagado entre eles automaticamente via environment.

## Campo `locationCode`

Todas as transações incluem um `locationCode` que o sistema usa para análise geo-aware de fraude. O valor é comparado com o `homeLocationCode` do perfil do cliente (seed: `SAO_PAULO_CENTRO` para todos os cartões de teste).

Lista completa de códigos disponíveis em `src/main/resources/geo/test-locations.json` (52 locais).

Códigos úteis para teste:

| Código | Local | Risco geo vs. SP |
|---|---|---|
| `SAO_PAULO_CENTRO` | São Paulo, Brasil | 0 (home) |
| `SAO_PAULO_PAULISTA` | Av. Paulista, Brasil | 0 (mesma cidade) |
| `RIO_DE_JANEIRO_CENTRO` | Rio de Janeiro, Brasil | baixo |
| `BUENOS_AIRES` | Argentina | +10 (país vizinho) |
| `LONDON` | Reino Unido | +20 (continente diferente) |
| `TOKYO` | Japão | +30 (país muito distante) |
| `NORTH_POLE` | Ártico | +40 (inabitado/implausível) |
| `ATLANTIC_OCEAN_MID` | Meio do Atlântico | +40 (inabitado/implausível) |

## Cenários

### Cenário 1 — Caminho Feliz
- **Cartão:** VISA `4111 1111 1111 1111`
- **locationCode:** `SAO_PAULO_PAULISTA` (mesma cidade que home)
- **Resultado esperado:** `APPROVED`

### Cenário 2 — Crédito Insuficiente
- **Cartão:** MASTER `5500 0055 5555 5559` (limite R$ 500, usado R$ 490)
- **locationCode:** `RIO_DE_JANEIRO_CENTRO`
- **Resultado esperado:** `REJECTED` (regra de negócio)

### Cenário 3 — Fraude via Cache Redis
- **Cartão:** VISA `4000 0000 0000 0002` (score 95 pré-carregado no Redis)
- **locationCode:** `SAO_PAULO_CENTRO`
- **Resultado esperado:** `REJECTED` imediato (score ≥ threshold 80)

### Cenário 4 — Fraude Geo (São Paulo → Tóquio)
- **Cartão:** VISA `4111 1111 1111 1111`
- **locationCode:** `TOKYO` (~18.500 km de distância, continente diferente)
- **Valor:** R$ 3.000
- **Resultado esperado:** `REJECTED` (penalidade geo +30 + alto valor)
- Requer profile `fraud-ollama` ou `fraud-bedrock`. Com `fraud-fallback` as regras estáticas também penalizam.

### Cenário 5 — Localização Implausível (Polo Norte)
- **Cartão:** VISA `4111 1111 1111 1111`
- **locationCode:** `NORTH_POLE` (inabitado, risk_hint ativo)
- **Resultado esperado:** `REJECTED` (penalidade +40)

### Utilitários
- **GET /status por ID manual** — consulta avulsa por correlationId
- **Idempotência** — reenvia `txn-happy-001`; deve retornar o mesmo `correlationId`
- **Validação** — body inválido; deve retornar `400 Validation Failed`

## Cartões de Teste (seed `seed-local.sql`)

| Número | Bandeira | Limite | Usado | Situação |
|---|---|---|---|---|
| `4111 1111 1111 1111` | VISA | R$ 10.000 | R$ 0 | Normal — VIP |
| `5500 0055 5555 5559` | MASTER | R$ 500 | R$ 490 | Crédito quase esgotado |
| `4000 0000 0000 0002` | VISA | R$ 10.000 | R$ 0 | Score de fraude 95 no Redis |
