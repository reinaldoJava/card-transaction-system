# Deployment Guide - Card Transaction System

Guia completo para deploy local e AWS.

## 📋 Índice

1. [Local Development](#local-development)
2. [AWS Production](#aws-production)
3. [Architecture](#architecture)
4. [Testing](#testing)

---

## Local Development

### Pré-requisitos

- Java 21+
- Maven 3.8+
- Docker & Docker Compose
- Git

### Setup

```bash
# Clone e entre no projeto
git clone <repo>
cd card-transaction-system

# Build do projeto
mvn clean package -DskipTests

# Start LocalStack + Ollama
docker-compose up -d

# Aguarde ~30s para LocalStack e Ollama ficarem prontos
docker-compose logs -f localstack ollama

# Run tests
mvn clean test
```

### Local Structure

```
Client (HTTP localhost:8080)
  ├─→ POST /api/auth/login        → Spring Controller
  ├─→ POST /api/transaction       → Spring Controller
  └─→ POST /api/token-exchange    → Spring Controller

Internamente:
  ├─ DynamoDB local (LocalStack)
  │  └─ Tabelas inicializadas por init-aws.sh
  │
  ├─ SQS local (LocalStack)
  │  └─ validation-queue.fifo
  │  └─ result-queue.fifo
  │
  ├─ Ollama local
  │  └─ Fraud analysis com Mistral
  │
  └─ Spring Boot (Tomcat 8080)
     └─ Feature Flag seleciona Ollama
```

### Running

```bash
# Terminal 1: Start Docker services
docker-compose up -d

# Terminal 2: Run Spring Boot
mvn spring-boot:run

# Terminal 3: Test
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "username": "test",
    "password": "test123"
  }'
```

### Configuration (Local)

Arquivo: `src/main/resources/application-local.yml`

```yaml
aws:
  dynamodb:
    endpoint: http://localhost:4566
    region: us-east-1

ollama:
  url: http://localhost:11434

fraud-analysis:
  provider: OLLAMA
```

### Cleaning Up Local

```bash
# Stop services
docker-compose down

# Remove volumes
docker-compose down -v
```

---

## AWS Production

### Prerequisites

1. **AWS Account** com credenciais configuradas
   ```bash
   aws configure
   ```

2. **Terraform** >= 1.5.0
   ```bash
   terraform -version
   ```

3. **S3 State** (criar uma única vez)
   ```bash
   aws s3api create-bucket \
     --bucket card-transaction-system-terraform-state \
     --region sa-east-1 \
     --create-bucket-configuration LocationConstraint=sa-east-1
   ```

4. **Bedrock Access** (se usar Bedrock)
   - Verify in AWS Console: Amazon Bedrock → Model access

### Deploy Steps

#### 1. Prepare Environment

```bash
cd terraform/

# Copy example variables
cp terraform.tfvars.example terraform.tfvars

# Edit with your values
nano terraform.tfvars
```

**Important variables**:
```hcl
jwt_secret = "your-very-secure-key-min-32-chars"
bedrock_model_id = "us.anthropic.claude-haiku-4-5-20251001-v1:0"
fraud_threshold = 80
```

#### 2. Build & Deploy

**Option A: Manual**
```bash
# Build JAR
mvn clean package -DskipTests

# Terraform init
terraform init

# Plan
terraform plan -out=tfplan

# Apply
terraform apply tfplan
```

**Option B: Automated Script**
```bash
chmod +x deploy.sh
./deploy.sh
```

#### 3. Verify Deployment

```bash
# Get endpoints
terraform output

# Test health
curl -X POST https://<process_transaction_url> \
  -H "Content-Type: application/json"

# Check logs
aws logs tail /aws/lambda/card-transaction-system-process-transaction --follow
```

### Architecture (AWS)

```
Client (HTTPS)
  ├─→ POST /          → Lambda processTransaction (Function URL)
  │   └─ Calls AWS SDK for SQS/DynamoDB
  │
  ├─→ POST /login     → Lambda login (Function URL)
  │
  └─→ POST /exchange  → Lambda tokenExchange (Function URL)

Event-Driven:
  SQS validation-queue
    ↓
  Lambda validateTransaction (Event Source Mapping)
    ↓
  DynamoDB + SQS result-queue
    ↓
  Client polls (from Lambda processTransaction)

Fraud Analysis:
  Lambda → Bedrock (AWS Anthropic Claude)
           or Ollama (local dev)
```

### Key Points

1. **Spring Cloud Functions** → Each Lambda runs Spring Boot with specific function
2. **Lambda Function URLs** → HTTPS endpoints (no API Gateway cost)
3. **PAY_PER_REQUEST** → DynamoDB on-demand (no provisioning)
4. **Feature Flags** → `fraud-analysis.provider=BEDROCK` in production

### Cost Estimate (Monthly)

| Service | Usage | Cost |
|---------|-------|------|
| Lambda | 1M invocations | $0.20 |
| DynamoDB | 25GB free | $0 |
| SQS | 1M requests free | $0 |
| Bedrock | 1M tokens | $0.80 |
| **Total** | | **~$1-5** |

---

## Architecture

### Local vs Production

```
┌─────────────────────────────────────────────────────┐
│                    LOCAL DEVELOPMENT                 │
├─────────────────────────────────────────────────────┤
│                                                       │
│  Client (HTTP:8080)                                  │
│    ↓                                                  │
│  Spring Boot (Single Instance)                       │
│    ├─→ DynamoDB Local (LocalStack)                   │
│    ├─→ SQS Local (LocalStack)                        │
│    ├─→ Ollama (Local LLM)                            │
│    └─→ Virtual Threads (Polling)                     │
│                                                       │
│  Tech: Java 21, Spring Boot 4.x, LocalStack, Ollama │
│                                                       │
└─────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────┐
│               AWS PRODUCTION (Serverless)            │
├─────────────────────────────────────────────────────┤
│                                                       │
│  Client (HTTPS) → Lambda Function URLs               │
│    ├─→ processTransaction                            │
│    ├─→ login                                         │
│    └─→ tokenExchange                                 │
│         ↓                                             │
│    DynamoDB (Fully Managed)                          │
│    SQS FIFO (Event-driven)                           │
│    Bedrock (AWS Claude)                              │
│                                                       │
│  Tech: Lambda, DynamoDB, SQS, Bedrock, Spring Cloud │
│                                                       │
└─────────────────────────────────────────────────────┘
```

### Request Flow (Prod)

```
1. Client POST /
   ↓
2. Lambda processTransaction (org.springframework.cloud.function.adapter.aws.FunctionInvoker)
   ↓
3. Spring Boot boots (SnapStart ~150ms)
   ├─ TransactionOrchestrator.orchestrate()
   ├─ Save to DynamoDB
   ├─ Publish to SQS validation-queue
   └─ Return correlationId to client
   ↓
4. Event Source Mapping (SQS → Lambda)
   ↓
5. Lambda validateTransaction
   ├─ Validate rules
   ├─ Call Bedrock for fraud analysis
   ├─ Update DynamoDB status
   └─ Publish to SQS result-queue
   ↓
6. Client receives result (polling from Lambda or via webhook)
```

---

## Testing

### Local E2E

```bash
# Terminal 1
docker-compose up -d
mvn spring-boot:run

# Terminal 2
# Test flow
bash test-local.sh
```

### AWS E2E

```bash
# After terraform apply
bash test-aws.sh <process_transaction_url> <jwt_token>
```

### Unit Tests

```bash
# Run all tests
mvn test

# Run specific
mvn test -Dtest=DynamoDbTransactionAdapterTest

# With coverage
mvn test jacoco:report
open target/site/jacoco/index.html
```

---

## Troubleshooting

### Local

**LocalStack not starting**
```bash
docker-compose logs localstack
docker-compose restart localstack
```

**Ollama not downloading model**
```bash
docker-compose logs ollama
# Wait 5-10 minutes for Mistral download
```

**Spring Boot port in use**
```bash
lsof -i :8080
kill -9 <PID>
```

### AWS

**Lambda timeout**
```hcl
# In terraform.tfvars
lambda_timeout = 60  # increase from 30
lambda_memory = 1024  # increase from 512
```

**No Bedrock access**
```bash
# Check in AWS Console
AWS Bedrock → Model access → Request access
```

**SQS messages stuck in DLQ**
```bash
aws sqs receive-message \
  --queue-url <dlq_url> \
  --max-number-of-messages 10
```

---

## Quick Reference

### Local Commands

```bash
# Build
mvn clean package -DskipTests

# Run tests
mvn test

# Start Spring Boot
mvn spring-boot:run

# Docker services
docker-compose up -d
docker-compose down
```

### AWS Commands

```bash
# Deploy
./deploy.sh

# View logs
aws logs tail /aws/lambda/card-transaction-system-process-transaction --follow

# Check SQS
aws sqs get-queue-attributes \
  --queue-url <url> \
  --attribute-names ApproximateNumberOfMessages

# Destroy (⚠️ CAREFUL)
cd terraform && terraform destroy
```

---

## Next Steps

1. ✅ Local development working
2. ✅ Tests passing
3. ⏳ AWS credentials configured
4. ⏳ S3 state bucket created
5. ⏳ `terraform apply` done
6. ⏳ Endpoints tested
7. ⏳ Monitor CloudWatch logs
8. ⏳ Setup CI/CD pipeline

---

## Support

For issues:
1. Check CloudWatch logs
2. Review terraform outputs
3. Verify IAM permissions
4. Check DynamoDB/SQS metrics in AWS Console

---

**Last Updated**: 2026-06-05
