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
