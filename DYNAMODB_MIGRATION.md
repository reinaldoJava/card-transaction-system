# DynamoDB Migration Complete ✓

Migração de JPA/H2 para DynamoDB completada com sucesso.

## Arquivos Criados

### Adapters DynamoDB (5)
- `DynamoDbTransactionAdapter` - Gerencia transações
- `DynamoDbUserRepositoryAdapter` - Gerencia usuários  
- `DynamoDbCacheAdapter` - Cache com TTL nativo
- `DynamoDbClientProfileAdapter` - Perfil de cliente
- `DynamoDbTransactionHistoryAdapter` - Histórico de transações

### Entities (5)
- `CardTransactionDdbEntity`
- `UserDdbEntity`
- `CacheDdbEntity`
- `ClientProfileDdbEntity`
- `TransactionHistoryDdbEntity`

### Config & Tests
- `DynamoDbConfig` - Bean configuration com suporte a LocalStack
- 3 testes de integração com Testcontainers

### Infraestrutura Local
- `docker-compose.yml` - LocalStack setup
- `init-aws.sh` - Script para criar tabelas
- `application-local.yml` - Spring Boot profile local
- `LOCALSTACK.md` - Documentação completa
- `Makefile` - Comandos úteis

## Arquivos Deletados

- `RegisterTransactionAdapter.java`
- `RegisterTransactionRepository.java`
- `CardTransactionEntity.java`
- `UserJpaRepository.java`
- `UserRepositoryAdapter.java`
- `UserEntity.java`
- Testes de integração JPA (2)

## Dependências Alteradas

```xml
<!-- Removidas -->
<spring-boot-starter-data-jpa/>
<h2/>
<spring-boot-starter-data-jpa-test/>

<!-- Adicionadas -->
<dynamodb/>
<dynamodb-enhanced/>
<testcontainers/>
<localstack/>
```

## Começar Rápido

### 1. Iniciar LocalStack
```bash
make localstack-up
# ou
docker-compose up -d
```

### 2. Executar Aplicação
```bash
make app-run
# ou
mvn spring-boot:run -Dspring-boot.run.arguments="--spring.profiles.active=local"
```

### 3. Executar Testes
```bash
make app-test
# ou
mvn test
```

### 4. Inspecionar Dados
```bash
make aws-list-tables
make aws-scan TABLE=card-transactions
```

## Configuração por Ambiente

### Desenvolvimento (LocalStack)
- Perfil: `local`
- Endpoint: `http://localhost:4566`
- Credenciais: test/test (mock)

### Produção (AWS)
- Perfil: `default` ou `aws`
- Endpoint: AWS endpoint real
- Credenciais: IAM roles

## Tabelas DynamoDB

| Tabela | PK | SK | Propósito |
|--------|----|----|-----------|
| `card-transactions` | `uuidTransaction` | - | Transações |
| `users` | `username` | - | Usuários |
| `cache` | `cacheKey` | - | Cache (TTL) |
| `client-profiles` | `cardToken` | - | Perfis |
| `transaction-history` | `cardToken` | - | Histórico |

## Próximos Passos

1. ✓ Implementar adapters DynamoDB
2. ✓ Configurar LocalStack
3. [ ] Validar testes de integração com dados reais
4. [ ] Ajustar IAM policies para produção
5. [ ] Configurar backup/point-in-time recovery
6. [ ] Performance testing com dados em escala

## Troubleshooting

**LocalStack não inicia?**
```bash
docker system prune
docker-compose down -v
docker-compose up -d
```

**Testes falham?**
```bash
# Verificar se LocalStack está rodando
docker-compose logs localstack

# Resetar dados
docker-compose down -v && docker-compose up -d
```

**Erro de conexão?**
- Verificar se porta 4566 está disponível
- Confirmar `application-local.yml` aponta para `http://localhost:4566`

## Referências

- [AWS DynamoDB](https://docs.aws.amazon.com/dynamodb/)
- [AWS SDK Java v2](https://docs.aws.amazon.com/sdk-for-java/)
- [LocalStack Documentation](https://docs.localstack.cloud/)
- [Testcontainers LocalStack](https://testcontainers.com/modules/localstack/)
