# CLAUDE.md — Instruções para o Agente

## ⛔ REGRAS ABSOLUTAS

### NUNCA FAÇA COMMITS
Preparar alterações (stage) é diferente de commitar.
Quando o usuário pedir para "preparar um commit" ou "criar um commit", o agente deve:
1. Listar claramente o que seria incluído e o que seria excluído (credenciais, arquivos desnecessários)
2. Atualizar o `.gitignore` se necessário
3. Apresentar o diff/resumo para revisão
4. **PARAR. Nunca executar `git commit`, `git push` ou qualquer operação destrutiva de git.**

O usuário executa o commit manualmente no terminal após revisar.

---

## Perfil Técnico

Staff/Senior Software Engineer — Java 25, Spring Boot 4.x, AWS (Lambda, DynamoDB, Step Functions, SSM), Arquitetura Hexagonal, DDD, Temporal (saga), Redis, Kafka/Redpanda.

## Regras de Ouro

- **Pare e Pergunte**: qualquer ambiguidade em regras de negócio, caminhos ou requisitos → PARE e pergunte antes de codificar. Não adivinhe.
- **Zero Explicações**: economize tokens ignorando introduções, resumos ou apologias. Fale apenas o estritamente necessário.
- **Código Completo**: entregue arquivos completos e prontos para uso, sem `// ...` ou omissões, para evitar erros de merge.
- **Sem Comentários**: código autoexplicativo (Clean Code). Exceção: workarounds técnicos complexos.
- **Nunca adivinhe**: se não tiver certeza do impacto de uma mudança, diga.

## Arquitetura

- **Hexagonal**: domínio puro isolado de frameworks. Ports (interfaces) no core; Adapters (Web, Kafka, DynamoDB, Postgres) na infra.
- **DDD**: entidades, agregados, value objects e domain services ricos e expressivos. Sem modelos anêmicos (proibido getters/setters cegos).
- **Clean Code**: funções pequenas, responsabilidade única (SRP), nomes significativos, tratamento de erros robusto sem misturar níveis de abstração.
- **Profile Groups**: `local-rich: cache-redis,queue-kafka,saga-temporal,ledger-postgres,fraud-ollama,env-local`
- **Saga**: Temporal SDK — `TransactionSagaWorkflowImpl` / `TransactionSagaActivitiesImpl`
- **Idempotência**: Redis por `transactionId`; status persistido no Postgres (`card_transactions`)

## TDD e Testes

- **Mentalidade TDD**: ao criar novas features, escreva ou apresente a classe de teste ANTES da implementação do código de produção.
- **Isolamento**: testes de unidade para o Domínio (mocks puros); testes de integração apenas para Adapters (LocalStack/Testcontainers).
- **Assertivas**: testes limpos, legíveis, cobrindo caminhos felizes e de exceção.

## Stack

- Java 25 — utilize recursos modernos: Records, Pattern Matching, Switch Expressions, Virtual Threads quando aplicável.
- Spring Boot 4.x
- AWS SDK Java v2 respeitando boas práticas de resiliência.

## Stack Local (docker-compose)

| Serviço       | Porta  | Observação                              |
|---------------|--------|-----------------------------------------|
| Postgres      | 5432   | Seed automático via `scripts/seed-local.sql` |
| Redis         | 6379   | + `redis-seed` seta score de fraude     |
| Redpanda      | 19092  | Kafka-compatible                        |
| Temporal      | 7233   | + UI em 8080                            |
| Ollama        | 11434  | + `ollama-setup` faz pull do mistral    |
| Jaeger        | 16686  |                                         |
| OTEL Collector| 4317   |                                         |

## Workflow

1. Abra uma tag `<thinking>` curta para planejar o design, a arquitetura e os testes antes de escrever qualquer código.
2. Gere os arquivos de teste.
3. Gere os arquivos de implementação completos, limpos e focados.

## Convenções Git

- Branch principal de dev: `developer`
- **Nunca commitar**: `.env`, `terraform.tfvars`, `*.tfstate*`, `AGENTS.md`, `HELP.md`
- Credenciais e senhas **jamais** vão para o repositório, mesmo que sejam valores de teste
- Senhas em Postman environments devem ser limpas antes de qualquer stage
