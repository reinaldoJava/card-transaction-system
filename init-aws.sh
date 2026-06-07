#!/bin/bash
set -e

echo "Initializing LocalStack resources..."

# DynamoDB tables
awslocal dynamodb create-table \
  --table-name card-transactions \
  --attribute-definitions AttributeName=uuidTransaction,AttributeType=S \
  --key-schema AttributeName=uuidTransaction,KeyType=HASH \
  --billing-mode PAY_PER_REQUEST \
  --region us-east-1 || echo "Table card-transactions already exists"

awslocal dynamodb create-table \
  --table-name users \
  --attribute-definitions AttributeName=username,AttributeType=S \
  --key-schema AttributeName=username,KeyType=HASH \
  --billing-mode PAY_PER_REQUEST \
  --region us-east-1 || echo "Table users already exists"

awslocal dynamodb create-table \
  --table-name cache \
  --attribute-definitions AttributeName=cacheKey,AttributeType=S \
  --key-schema AttributeName=cacheKey,KeyType=HASH \
  --billing-mode PAY_PER_REQUEST \
  --region us-east-1 || echo "Table cache already exists"

awslocal dynamodb update-time-to-live \
  --table-name cache \
  --time-to-live-specification AttributeName=expiresAt,Enabled=true \
  --region us-east-1 || echo "TTL on cache already configured"

awslocal dynamodb create-table \
  --table-name client-profiles \
  --attribute-definitions AttributeName=cardToken,AttributeType=S \
  --key-schema AttributeName=cardToken,KeyType=HASH \
  --billing-mode PAY_PER_REQUEST \
  --region us-east-1 || echo "Table client-profiles already exists"

awslocal dynamodb create-table \
  --table-name transaction-history \
  --attribute-definitions AttributeName=cardToken,AttributeType=S \
  --key-schema AttributeName=cardToken,KeyType=HASH \
  --billing-mode PAY_PER_REQUEST \
  --region us-east-1 || echo "Table transaction-history already exists"

echo "DynamoDB tables initialized."

# Step Functions state machine (local mock - Pass state)
SAGA_DEFINITION='{"Comment":"Card transaction saga (local mock)","StartAt":"Process","States":{"Process":{"Type":"Pass","End":true}}}'

awslocal stepfunctions create-state-machine \
  --name card-transaction-system-transaction-saga \
  --definition "$SAGA_DEFINITION" \
  --role-arn arn:aws:iam::000000000000:role/local-sfn-role \
  --region us-east-1 || echo "State machine already exists"

echo "Step Functions state machine initialized."

# SSM parameters
awslocal ssm put-parameter \
  --name /card-transaction-system/jwt-secret \
  --value "local-dev-secret-key-exactly-32bytes" \
  --type SecureString \
  --region us-east-1 || echo "SSM jwt-secret already exists"

awslocal ssm put-parameter \
  --name /card-transaction-system/nr-license-key \
  --value "local-placeholder-nr-key" \
  --type SecureString \
  --region us-east-1 || echo "SSM nr-license-key already exists"

echo "SSM parameters initialized."
echo "LocalStack initialization complete."
