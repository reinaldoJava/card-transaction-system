# Structured Logging & CloudWatch Integration

## Overview

Sistema de logging estruturado em JSON com correlação de transações (MDC) pronto para AWS CloudWatch.

## Stack

- **SLF4J**: Facade de logging
- **Logback**: Implementação com output JSON
- **Logstash Encoder**: Estrutura logs em JSON
- **MDC (Mapped Diagnostic Context)**: Rastreamento de correlação

## Features

### 1. JSON Output
Todos os logs são estruturados em JSON para parsing automático em CloudWatch:

```json
{
  "@timestamp": "2026-06-06T15:30:45.123Z",
  "service": "card-transaction-system",
  "level": "INFO",
  "message": "Transaction processed",
  "correlationId": "550e8400-e29b-41d4-a716-446655440000",
  "userId": "user-123",
  "transactionId": "txn-456",
  "duration_ms": 245
}
```

### 2. Automatic Correlation Tracking
Cada request recebe um único `X-Correlation-ID`:

```bash
# Header propagado automaticamente
X-Correlation-ID: 550e8400-e29b-41d4-a716-446655440000
```

Disponível em `MDC.get("correlationId")` em toda a call stack.

### 3. Automatic Method Logging
Aspect registra entrada/saída de métodos:

```
DEBUG: Entering TransactionService.process with args: [CardTransactionRequest]
DEBUG: Exiting TransactionService.process with duration: 245ms
ERROR: Exception in TransactionService.process after 123ms: Insufficient funds
```

## Usage

### Basic Logging

```java
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TransactionService {
    private static final Logger logger = LoggerFactory.getLogger(TransactionService.class);

    public void processTransaction(CardTransactionRequest request) {
        logger.info("Processing transaction for user: {}", request.cardData().name());
        // ...
        logger.debug("Transaction details: amount={}, status={}", request.amount(), status);
    }
}
```

### Structured Logging with Context

```java
import com.empresa.cardtransactionsystem.adapters.inbound.logging.StructuredLogger;
import org.slf4j.LoggerFactory;

public class TransactionController {
    private static final Logger logger = LoggerFactory.getLogger(TransactionController.class);

    public ResponseEntity<?> processTransaction(CardTransactionRequest request) {
        StructuredLogger log = StructuredLogger.of(logger, getCorrelationId());
        log.addContext("userId", request.username());
        log.addContext("amount", request.amount());

        try {
            TransactionResult result = transactionService.process(request);
            log.info("Transaction approved", "transactionId", result.uuidTransaction().toString());
            return ResponseEntity.ok(result);
        } catch (Exception ex) {
            log.error("Transaction failed", ex, "error", ex.getMessage());
            throw ex;
        } finally {
            log.clear();
        }
    }
}
```

## Configuration

### application.yml

```yaml
logging:
  level:
    root: INFO
    com.empresa.cardtransactionsystem: DEBUG
  file:
    name: logs/application.log

spring:
  profiles:
    active: local
```

### Local Development

Logs são salvos em JSON em:
```
logs/application.log
logs/application-2026-06-06-1.log.gz  # Rotação automática
```

Visualizar:

```bash
tail -f logs/application.log | jq .
```

## CloudWatch Integration

### Deploy para AWS Lambda

1. **Dockerfile automaticamente envia logs para CloudWatch**:
   ```dockerfile
   FROM public.ecr.aws/lambda/java:25
   ENV SPRING_PROFILES_ACTIVE=!local
   ```

2. **CloudWatch Logs Group** (criado automaticamente por Lambda):
   ```
   /aws/lambda/card-transaction-system
   ```

3. **Logs são estruturados em JSON** e parseados por CloudWatch:
   - Buscar por correlationId
   - Filtrar por level, duration_ms, error
   - Criar métricas customizadas

### CloudWatch Insights Queries

```sql
-- Buscar todas as transações de um usuário
fields @timestamp, message, duration_ms
| filter userId = "user-123"
| stats avg(duration_ms) as avg_duration by transactionId

-- Transações com erro
fields @timestamp, message, stack_trace
| filter level = "ERROR"
| stats count() as error_count by error

-- Performance por endpoint
fields @timestamp, duration_ms, path
| stats avg(duration_ms), max(duration_ms) by path

-- Rastrear uma transação específica
fields @timestamp, message, logger_name, level
| filter correlationId = "550e8400-e29b-41d4-a716-446655440000"
```

### Alarms

Criar alarms para:

```
Métrica: ErrorCount
Condição: > 5 errors em 5 minutos
Ação: SNS notification
```

## MDC Context Keys

| Key | Fonte | Descrição |
|-----|-------|-----------|
| `correlationId` | CorrelationIdFilter | ID único por request |
| `method` | CorrelationIdFilter | HTTP method (GET, POST, etc) |
| `path` | CorrelationIdFilter | Request path |
| `userId` | Application | User ID (manual) |
| `transactionId` | Application | Transaction ID (manual) |
| `duration_ms` | LoggingAspect | Tempo de execução |
| `error` | LoggingAspect | Tipo de exception |

## Free Tier Limits

AWS CloudWatch Free Tier:
- **5 GB/mês**: Log ingestion
- **10 custom metrics**: Sem custo
- **Log retention**: Configurável (default 7 dias)

Para 1000 requisições/dia com logs médios:
- ~50 MB/mês ✅ Dentro do free tier

## Troubleshooting

### Logs não aparecem em JSON

1. Verificar `logback-spring.xml` está em `src/main/resources`
2. Verificar `spring-boot-starter-aop` no pom.xml
3. Verificar `SPRING_PROFILES_ACTIVE` não desativa logging

### CloudWatch não recebe logs

1. Verificar IAM role tem permissão `logs:CreateLogGroup`, `logs:CreateLogStream`, `logs:PutLogEvents`
2. Verificar Lambda execution role está configurada
3. Verificar region do CloudWatch

### Performance degradada

1. Reduzir LoggingAspect scope (remover classes não críticas)
2. Aumentar `maxHistory` e `totalSizeCap` em logback-spring.xml
3. Usar `@Log` annotations com `skipLogging=true` para métodos sensíveis

## Production Checklist

- [ ] Logs salvos em JSON
- [ ] CorrelationId propagado em todos os requests
- [ ] MDC context limpo em finally blocks
- [ ] Sensitive data (passwords, tokens) não é logado
- [ ] Log level é INFO em produção
- [ ] CloudWatch alarms configurados
- [ ] Retention policy definida (ex: 7 dias)
- [ ] Testes de logging executam sem erros
