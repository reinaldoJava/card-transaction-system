resource "aws_ssm_parameter" "jwt_secret" {
  name        = "/${var.project_name}/jwt-secret"
  description = "JWT signing secret for ${var.project_name}"
  type        = "SecureString"
  value       = var.jwt_secret

  lifecycle {
    ignore_changes = [value]
  }
}

resource "aws_ssm_parameter" "nr_license_key" {
  name        = "/${var.project_name}/nr-license-key"
  description = "New Relic license key for ${var.project_name} OTLP traces export"
  type        = "SecureString"
  value       = var.nr_license_key

  lifecycle {
    ignore_changes = [value]
  }
}

resource "aws_ssm_parameter" "bedrock_system_prompt" {
  name        = "/${var.project_name}/fraud/bedrock/system-prompt"
  description = "Bedrock fraud analysis system prompt — edit directly in SSM to update without redeploy"
  type        = "String"
  value       = file("${path.module}/../scripts/prompts/bedrock-fraud-system.txt")

  lifecycle {
    ignore_changes = [value]
  }
}
