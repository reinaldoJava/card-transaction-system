# LocalStack Setup for DynamoDB Development

Este documento descreve como configurar e executar o LocalStack para desenvolvimento local com DynamoDB.

## Pré-requisitos

- Docker e Docker Compose instalados
- Java 25+
- Maven 3.9+

## Iniciar LocalStack

```bash
docker-compose up -d
```

Isso irá:
1. Iniciar o container LocalStack com suporte a DynamoDB
2. Executar o script `init-aws.sh` para criar as tabelas necessárias
3. Expor a API na porta `4566`

### Verificar se LocalStack está pronto

```bash
docker-compose logs localstack
```

Procure pela mensagem: `DynamoDB tables initialized successfully!`

## Tabelas DynamoDB Criadas

| Tabela | Chave Primária | Propósito |
|--------|---|---|
| `card-transactions` | `uuidTransaction` (S) | Armazena transações de cartão |
| `users` | `username` (S) | Armazena dados de usuários |
| `cache` | `cacheKey` (S) | Cache com TTL para fraud scores, idempotência, perfis |
| `client-profiles` | `cardToken` (S) | Perfil de cliente por token |
| `transaction-history` | `cardToken` (S) | Histórico de transações por cartão |

## Executar a Aplicação Localmente

### Com LocalStack

```bash
mvn spring-boot:run -Dspring-boot.run.arguments="--spring.profiles.active=local"
```

Ou configure em `application-local.yml`:
```yaml
spring.profiles.active: local
aws.dynamodb.endpoint: http://localhost:4566
aws.dynamodb.region: us-east-1
```

### Com AWS (produção)

```bash
mvn spring-boot:run
```

## Executar Testes de Integração

Os testes esperan que o LocalStack esteja rodando:

```bash
# Terminal 1: Iniciar LocalStack
docker-compose up -d

# Terminal 2: Executar testes
mvn test
```

## Comandos Úteis

### Listar tabelas
```bash
aws dynamodb list-tables \
  --endpoint-url http://localhost:4566 \
  --region us-east-1
```

### Escanear uma tabela
```bash
aws dynamodb scan \
  --table-name card-transactions \
  --endpoint-url http://localhost:4566 \
  --region us-east-1
```

### Deletar uma tabela
```bash
aws dynamodb delete-table \
  --table-name card-transactions \
  --endpoint-url http://localhost:4566 \
  --region us-east-1
```

### Parar LocalStack
```bash
docker-compose down
```

### Limpar dados do LocalStack (reset)
```bash
docker-compose down -v
```

## Troubleshooting

### LocalStack não conecta
```bash
# Verificar logs
docker-compose logs -f localstack

# Reiniciar
docker-compose restart
```

### Tabelas não estão sendo criadas
```bash
# Verificar se o script init-aws.sh tem permissão de execução
chmod +x init-aws.sh

# Reiniciar LocalStack
docker-compose down && docker-compose up -d
```

### Credenciais
- Access Key: `test`
- Secret Key: `test`
- Region: `us-east-1`
- Endpoint: `http://localhost:4566`

## Arquitetura

```
┌─────────────────────┐
│   Spring Boot App    │
│   (Port 8080)       │
└──────────┬──────────┘
           │
           │ AWS SDK v2
           │
┌──────────▼──────────┐
│   LocalStack        │
│   (Port 4566)       │
│  ┌────────────────┐ │
│  │  DynamoDB      │ │
│  │  Tables:       │ │
│  │  - transactions│ │
│  │  - users       │ │
│  │  - cache (TTL) │ │
│  │  - profiles    │ │
│  │  - history     │ │
│  └────────────────┘ │
└─────────────────────┘
```

## Próximos Passos

1. Iniciar LocalStack: `docker-compose up -d`
2. Executar a app: `mvn spring-boot:run -Dspring-boot.run.arguments="--spring.profiles.active=local"`
3. Executar testes: `mvn test`
4. Verificar dados: AWS CLI ou console LocalStack

---

**Nota**: Não comita credenciais reais no código. Use `application-local.yml` apenas para desenvolvimento local.
