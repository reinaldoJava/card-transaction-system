resource "aws_dynamodb_table" "card_transactions" {
  name           = "card-transactions"
  billing_mode   = var.dynamodb_billing_mode
  hash_key       = "uuidTransaction"

  attribute {
    name = "uuidTransaction"
    type = "S"
  }

  attribute {
    name = "cardToken"
    type = "S"
  }

  global_secondary_index {
    name            = "cardToken-index"
    hash_key        = "cardToken"
    projection_type = "ALL"
  }
}

resource "aws_dynamodb_table" "users" {
  name           = "users"
  billing_mode   = var.dynamodb_billing_mode
  hash_key       = "username"
  attribute {
    name = "username"
    type = "S"
  }
}

resource "aws_dynamodb_table" "cache" {
  name           = "cache"
  billing_mode   = var.dynamodb_billing_mode
  hash_key       = "cacheKey"
  attribute {
    name = "cacheKey"
    type = "S"
  }

  ttl {
    attribute_name = "expiresAt"
    enabled        = true
  }
}

resource "aws_dynamodb_table" "client_profiles" {
  name           = "client-profiles"
  billing_mode   = var.dynamodb_billing_mode
  hash_key       = "cardToken"
  attribute {
    name = "cardToken"
    type = "S"
  }
}

resource "aws_dynamodb_table" "transaction_history" {
  name           = "transaction-history"
  billing_mode   = var.dynamodb_billing_mode
  hash_key       = "cardToken"
  attribute {
    name = "cardToken"
    type = "S"
  }
}
