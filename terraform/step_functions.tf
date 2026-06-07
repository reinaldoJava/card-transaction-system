resource "aws_iam_role" "step_functions_role" {
  name = "${var.project_name}-step-functions-role"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Action    = "sts:AssumeRole"
        Effect    = "Allow"
        Principal = { Service = "states.amazonaws.com" }
      }
    ]
  })
}

resource "aws_iam_role_policy" "step_functions_invoke_lambda" {
  name = "${var.project_name}-step-functions-invoke-lambda"
  role = aws_iam_role.step_functions_role.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect = "Allow"
        Action = ["lambda:InvokeFunction"]
        Resource = [
          "${aws_lambda_function.validate_rules.arn}:*",
          "${aws_lambda_function.fraud_analysis.arn}:*",
          "${aws_lambda_function.update_status.arn}:*",
          "${aws_lambda_function.compensation.arn}:*",
          aws_lambda_function.validate_rules.arn,
          aws_lambda_function.fraud_analysis.arn,
          aws_lambda_function.update_status.arn,
          aws_lambda_function.compensation.arn
        ]
      }
    ]
  })
}

resource "aws_iam_role_policy" "step_functions_dynamodb" {
  name = "${var.project_name}-step-functions-dynamodb"
  role = aws_iam_role.step_functions_role.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect = "Allow"
        Action = ["dynamodb:GetItem", "dynamodb:UpdateItem"]
        Resource = [
          aws_dynamodb_table.card_transactions.arn,
          aws_dynamodb_table.cache.arn
        ]
      }
    ]
  })
}

locals {
  saga_state_machine = <<-EOT
{
  "Comment": "Card Transaction Saga - Validation, Fraud Analysis, and Status Update",
  "StartAt": "ValidateBusinessRules",
  "States": {
    "ValidateBusinessRules": {
      "Type": "Task",
      "Resource": "${aws_lambda_function.validate_rules.arn}",
      "ResultPath": null,
      "TimeoutSeconds": 30,
      "Retry": [
        {
          "ErrorEquals": ["States.TaskFailed"],
          "IntervalSeconds": 2,
          "MaxAttempts": 2,
          "BackoffRate": 2
        }
      ],
      "Catch": [
        {
          "ErrorEquals": ["States.ALL"],
          "Next": "RollbackCompensation"
        }
      ],
      "Next": "AnalyzeFraud"
    },
    "AnalyzeFraud": {
      "Type": "Task",
      "Resource": "${aws_lambda_function.fraud_analysis.arn}",
      "ResultPath": "$.fraudScore",
      "TimeoutSeconds": 30,
      "Retry": [
        {
          "ErrorEquals": ["States.TaskFailed"],
          "IntervalSeconds": 2,
          "MaxAttempts": 2,
          "BackoffRate": 2
        }
      ],
      "Catch": [
        {
          "ErrorEquals": ["States.ALL"],
          "Next": "RollbackCompensation"
        }
      ],
      "Next": "UpdateStatus"
    },
    "UpdateStatus": {
      "Type": "Task",
      "Resource": "${aws_lambda_function.update_status.arn}",
      "Parameters": {
        "transactionId.$": "$.transactionId",
        "uuidTransaction.$": "$.correlationId",
        "fraudScore.$": "$.fraudScore",
        "traceparent.$": "$.traceparent"
      },
      "TimeoutSeconds": 30,
      "End": true
    },
    "RollbackCompensation": {
      "Type": "Task",
      "Resource": "${aws_lambda_function.compensation.arn}",
      "TimeoutSeconds": 30,
      "End": true
    }
  }
}
EOT
}

resource "aws_sfn_state_machine" "transaction_saga" {
  name       = "${var.project_name}-transaction-saga"
  role_arn   = aws_iam_role.step_functions_role.arn
  definition = local.saga_state_machine
  type       = "STANDARD"

  logging_configuration {
    log_destination        = "${aws_cloudwatch_log_group.step_functions.arn}:*"
    include_execution_data = false
    level                  = "ERROR"
  }
}

resource "aws_cloudwatch_log_group" "step_functions" {
  name              = "/aws/step-functions/${var.project_name}"
  retention_in_days = 7
}

output "step_functions_state_machine_arn" {
  description = "Step Functions State Machine ARN"
  value       = aws_sfn_state_machine.transaction_saga.arn
}
