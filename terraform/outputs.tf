output "process_transaction_url" {
  description = "URL endpoint for process transaction Lambda"
  value       = aws_lambda_function_url.process_transaction_url.function_url
}

output "login_url" {
  description = "URL endpoint for login Lambda"
  value       = aws_lambda_function_url.login_url.function_url
}

output "token_exchange_url" {
  description = "URL endpoint for token exchange Lambda"
  value       = aws_lambda_function_url.token_exchange_url.function_url
}

output "nr_license_key_ssm_path" {
  description = "SSM path for New Relic license key"
  value       = aws_ssm_parameter.nr_license_key.name
}

output "dynamodb_tables" {
  description = "DynamoDB table names"
  value = {
    card_transactions   = aws_dynamodb_table.card_transactions.name
    users               = aws_dynamodb_table.users.name
    cache               = aws_dynamodb_table.cache.name
    client_profiles     = aws_dynamodb_table.client_profiles.name
    transaction_history = aws_dynamodb_table.transaction_history.name
  }
}

output "lambda_role_arn" {
  description = "Lambda execution role ARN"
  value       = aws_iam_role.lambda_role.arn
}
