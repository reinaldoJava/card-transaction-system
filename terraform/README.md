# Terraform - AWS Deployment

Infraestrutura como código para deploy do Card Transaction System na AWS.

## Estrutura

```
terraform/
├── main.tf              # Configuração principal, provider, backend
├── variables.tf         # Definição de variáveis
├── terraform.tfvars     # Valores das variáveis (customize)
├── iam.tf              # Roles e policies Lambda
├── dynamodb.tf         # Tabelas DynamoDB
├── sqs.tf              # Filas SQS FIFO
├── lambda.tf           # Funções Lambda com Function URLs
├── outputs.tf          # Outputs (URLs dos endpoints)
└── README.md           # Este arquivo
```

## Pré-requisitos

1. **AWS CLI** configurada com credenciais
   ```bash
   aws configure
   ```

2. **Terraform** >= 1.5.0
   ```bash
   terraform version
   ```

3. **Maven build** do JAR
   ```bash
   mvn clean package -DskipTests
   ```

4. **S3 bucket** para Terraform state (criar uma única vez)
   ```bash
   aws s3api create-bucket \
     --bucket card-transaction-system-terraform-state \
     --region sa-east-1 \
     --create-bucket-configuration LocationConstraint=sa-east-1
   
   aws s3api put-bucket-versioning \
     --bucket card-transaction-system-terraform-state \
     --versioning-configuration Status=Enabled
   ```

5. **DynamoDB table** para locks (criar uma única vez)
   ```bash
   aws dynamodb create-table \
     --table-name terraform-locks \
     --attribute-definitions AttributeName=LockID,AttributeType=S \
     --key-schema AttributeName=LockID,KeyType=HASH \
     --provisioned-throughput ReadCapacityUnits=5,WriteCapacityUnits=5 \
     --region sa-east-1
   ```

## Deploy

### 1. Preparar variáveis

Edite `terraform.tfvars` e customize:
```hcl
jwt_secret = "seu-secret-seguro"
bedrock_model_id = "us.anthropic.claude-haiku-4-5-20251001-v1:0"
fraud_threshold = 80
```

### 2. Inicializar Terraform

```bash
cd terraform/
terraform init
```

### 3. Planejar deployment

```bash
terraform plan -out=tfplan
```

### 4. Aplicar infraestrutura

```bash
terraform apply tfplan
```

Terraform vai:
- Criar 5 tabelas DynamoDB
- Criar 4 filas SQS FIFO (2 queues + 2 DLQs)
- Deploy 3 Lambdas com Function URLs
- Configurar IAM roles/policies
- Gerar endpoints HTTP para cada Lambda

### 5. Outputs

Após `terraform apply`, você terá:

```
process_transaction_url = https://xxx.lambda-url.sa-east-1.on.aws
login_url               = https://yyy.lambda-url.sa-east-1.on.aws
token_exchange_url      = https://zzz.lambda-url.sa-east-1.on.aws
validation_queue_url    = https://sqs.sa-east-1.amazonaws.com/...
result_queue_url        = https://sqs.sa-east-1.amazonaws.com/...
```

## Arquitetura

```
Client (HTTPS)
  ├─→ POST /process-transaction  → Lambda processTransaction
  ├─→ POST /login                → Lambda login
  └─→ POST /token-exchange       → Lambda tokenExchange

Internamente:
  ├─ DynamoDB (5 tabelas)
  │  ├─ card-transactions
  │  ├─ users
  │  ├─ cache (com TTL)
  │  ├─ client-profiles
  │  └─ transaction-history
  │
  ├─ SQS (2 FIFO + 2 DLQs)
  │  ├─ validation-queue → Lambda validateTransaction
  │  └─ result-queue
  │
  └─ Bedrock (fraud analysis)
```

## Variáveis importantes

| Variável | Descrição | Default |
|----------|-----------|---------|
| `aws_region` | Região AWS | sa-east-1 |
| `lambda_memory` | Memória Lambda (MB) | 512 |
| `lambda_timeout` | Timeout Lambda (s) | 30 |
| `bedrock_model_id` | Modelo Bedrock | claude-haiku |
| `fraud_threshold` | Score fraud limit | 80 |
| `jwt_secret` | Secret JWT (⚠️ change!) | default |

## Monitoramento

Após deploy, monitore via CloudWatch:

```bash
# Logs Lambda
aws logs tail /aws/lambda/card-transaction-system-process-transaction --follow

# Métricas SQS
aws sqs get-queue-attributes \
  --queue-url <validation_queue_url> \
  --attribute-names ApproximateNumberOfMessages
```

## Cleanup

Para destruir toda a infraestrutura:

```bash
terraform destroy
```

⚠️ **Aviso**: Isso vai deletar todas as tabelas e filas. Backup DynamoDB antes!

## Troubleshooting

### Lambda timeout ou erro 30s

Aumente `lambda_timeout` e `lambda_memory`:
```hcl
lambda_memory  = 1024  # de 512
lambda_timeout = 60    # de 30
```

### DynamoDB ProvisionedThroughputExceededException

Use `PAY_PER_REQUEST` (já configurado):
```hcl
dynamodb_billing_mode = "PAY_PER_REQUEST"
```

### JWT_SECRET não carregando

Verifique em `terraform.tfvars`:
```bash
grep jwt_secret terraform.tfvars
```

## Cost

Estimativa mensal (free tier):
- **DynamoDB**: ~$0 (25GB free)
- **SQS**: ~$0 (1M requests free)
- **Lambda**: ~$0.20 (1M invocations free + compute)
- **Bedrock**: $0.80 por 1M input tokens

**Total**: ~$1-5/mês (depende de uso)
