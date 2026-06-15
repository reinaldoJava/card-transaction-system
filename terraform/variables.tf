variable "aws_region" {
  description = "AWS region"
  type        = string
  default     = "us-east-1"
}

variable "project_name" {
  description = "Project name"
  type        = string
  default     = "card-transaction-system"
}

variable "environment" {
  description = "Environment"
  type        = string
  default     = "prod"
}

variable "lambda_memory" {
  description = "Lambda memory in MB"
  type        = number
  default     = 512
}

variable "lambda_timeout" {
  description = "Lambda timeout in seconds"
  type        = number
  default     = 30
}

variable "bedrock_model_id" {
  description = "Bedrock model ID for fraud analysis"
  type        = string
  default     = "us.anthropic.claude-haiku-4-5-20251001-v1:0"
}

variable "fraud_threshold" {
  description = "Fraud score threshold"
  type        = number
  default     = 80
}

variable "dynamodb_billing_mode" {
  description = "DynamoDB billing mode"
  type        = string
  default     = "PAY_PER_REQUEST"
}

variable "jwt_secret" {
  description = "JWT secret key"
  type        = string
  sensitive   = true
}

variable "jwt_expiration_hours" {
  description = "JWT expiration in hours"
  type        = number
  default     = 24
}

variable "allowed_origins" {
  description = "Allowed CORS origins for Lambda Function URLs (e.g. [\"https://app.example.com\"])"
  type        = list(string)
}

variable "nr_license_key" {
  description = "New Relic license key for OTLP traces export"
  type        = string
  sensitive   = true
  default     = ""
}

variable "otel_exporter_endpoint" {
  description = "OTLP exporter endpoint base URL"
  type        = string
  default     = "https://otlp.nr-data.net"
}

variable "nr_account_id" {
  description = "New Relic account ID"
  type        = number
}

variable "nr_api_key" {
  description = "New Relic User API key (NRAK-...)"
  type        = string
  sensitive   = true
}

variable "nr_region" {
  description = "New Relic data center region (US or EU)"
  type        = string
  default     = "US"
}
