# CI/CD: GitHub Actions

Dois workflows, sem chaves de acesso estáticas (autenticação **OIDC**):

| Workflow | Arquivo | Gatilho | O que faz |
|---|---|---|---|
| CI | `.github/workflows/ci.yml` | push em `developer`, PR para `developer`/`main` | `./mvnw clean verify` (build + testes) |
| Deploy | `.github/workflows/deploy.yml` | **manual** (`workflow_dispatch`), input `plan`/`apply` | build do jar → OIDC → `terraform plan`/`apply` |

O deploy é **manual e gated**: nunca dispara em push. `plan` é o default; `apply` exige escolha explícita.

---

## 1. Bootstrap (uma única vez): OIDC Provider + IAM Role

A role é assumida pelo GitHub via OIDC; sem `AWS_ACCESS_KEY_ID`/`SECRET` em lugar nenhum.

```bash
# 1) Registrar o GitHub como OIDC provider (idempotente; ignore se ja existir)
aws iam create-open-id-connect-provider \
  --url https://token.actions.githubusercontent.com \
  --client-id-list sts.amazonaws.com \
  --thumbprint-list 6938fd4d98bab03faadb97b34396831e3780aea1
```

Trust policy da role (`trust.json`), **restrinja ao seu repositório** (evita que outro repo assuma a role):

```json
{
  "Version": "2012-10-17",
  "Statement": [{
    "Effect": "Allow",
    "Principal": { "Federated": "arn:aws:iam::<ACCOUNT_ID>:oidc-provider/token.actions.githubusercontent.com" },
    "Action": "sts:AssumeRoleWithWebIdentity",
    "Condition": {
      "StringEquals": { "token.actions.githubusercontent.com:aud": "sts.amazonaws.com" },
      "StringLike": { "token.actions.githubusercontent.com:sub": "repo:<OWNER>/<REPO>:*" }
    }
  }]
}
```

```bash
aws iam create-role \
  --role-name card-transaction-system-gha-deploy \
  --assume-role-policy-document file://trust.json
```

Anexe à role uma policy com o necessário para o deploy: `lambda:*`, `dynamodb:*`, `states:*`, `iam:PassRole`/`iam:*` (escopado aos recursos do projeto), `ssm:*` nos paths `/card-transaction-system/*`, `bedrock:InvokeModel`, e acesso ao bucket de state + tabela de lock. Comece restrito e amplie conforme o `plan` reclamar.

> Dica: para produção real, troque os `*` por ações mínimas por recurso. Para um projeto público de portfólio, escopar por `Resource` ARN do projeto já é um bom equilíbrio.

---

## 2. GitHub Secrets (Settings → Secrets and variables → Actions)

| Secret | Conteúdo |
|---|---|
| `AWS_DEPLOY_ROLE_ARN` | ARN da role acima (`arn:aws:iam::<ACCOUNT_ID>:role/card-transaction-system-gha-deploy`) |
| `TF_VAR_JWT_SECRET` | segredo JWT (≥ 32 chars), vai para o SSM via Terraform |
| `TF_VAR_NR_LICENSE_KEY` | New Relic license key (ou vazio) |
| `TF_VAR_NR_ACCOUNT_ID` | New Relic account id |
| `TF_VAR_NR_API_KEY` | New Relic API key |

Nenhum desses valores entra no repositório. O workflow os injeta como `TF_VAR_*` só no passo do Terraform.

---

## 3. Proteção do ambiente `production`

Em **Settings → Environments → production**:

- **Required reviewers**: exige aprovação humana antes do `apply` (recomendado).
- Opcional: restringir a branches `main`.

Assim, mesmo `apply` manual passa por aprovação.

---

## 4. Usar

GitHub → aba **Actions** → **Deploy (AWS)** → **Run workflow** → escolha `plan` (revisar) ou `apply`.

Pré-requisitos antes do primeiro deploy (ver `terraform/README.md`): bucket S3 de state + tabela de lock criados, e **Bedrock model access** habilitado para o Claude Haiku em `us-east-1`.
