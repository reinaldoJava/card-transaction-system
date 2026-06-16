resource "aws_lambda_function" "validate_transaction" {
  filename         = data.archive_file.lambda_jar.output_path
  function_name    = "${var.project_name}-validate-transaction"
  role             = aws_iam_role.lambda_role.arn
  handler          = local.lambda_handler
  runtime          = "java21"
  source_code_hash = data.archive_file.lambda_jar.output_base64sha256
  memory_size      = var.lambda_memory
  timeout          = var.lambda_timeout
  architectures    = ["arm64"]
  publish          = true

  snap_start {
    apply_on = "PublishedVersions"
  }

  environment {
    variables = merge(local.otel_common, {
      SPRING_CLOUD_FUNCTION_DEFINITION = "validateTransactionFunction"
      SPRING_PROFILES_ACTIVE           = "!local"
      AWS_REGION                       = var.aws_region
      OTEL_SERVICE_NAME                = "${var.project_name}-validate-transaction"
    })
  }

  depends_on = [
    aws_iam_role_policy_attachment.lambda_basic,
    aws_iam_role_policy.lambda_dynamodb,
    aws_iam_role_policy.lambda_ssm
  ]
}

resource "aws_lambda_function" "validate_rules" {
  filename         = data.archive_file.lambda_jar.output_path
  function_name    = "${var.project_name}-validate-rules"
  role             = aws_iam_role.lambda_role.arn
  handler          = local.lambda_handler
  runtime          = "java21"
  source_code_hash = data.archive_file.lambda_jar.output_base64sha256
  memory_size      = var.lambda_memory
  timeout          = var.lambda_timeout
  architectures    = ["arm64"]
  publish          = true

  snap_start {
    apply_on = "PublishedVersions"
  }

  environment {
    variables = merge(local.otel_common, {
      SPRING_CLOUD_FUNCTION_DEFINITION = "validateBusinessRulesFunction"
      SPRING_PROFILES_ACTIVE           = "!local"
      AWS_REGION                       = var.aws_region
      OTEL_SERVICE_NAME                = "${var.project_name}-validate-rules"
    })
  }

  depends_on = [
    aws_iam_role_policy_attachment.lambda_basic,
    aws_iam_role_policy.lambda_dynamodb,
    aws_iam_role_policy.lambda_ssm
  ]
}

resource "aws_lambda_function" "fraud_analysis" {
  filename         = data.archive_file.lambda_jar.output_path
  function_name    = "${var.project_name}-fraud-analysis"
  role             = aws_iam_role.lambda_role.arn
  handler          = local.lambda_handler
  runtime          = "java21"
  source_code_hash = data.archive_file.lambda_jar.output_base64sha256
  memory_size      = var.lambda_memory
  timeout          = var.lambda_timeout
  architectures    = ["arm64"]
  publish          = true

  snap_start {
    apply_on = "PublishedVersions"
  }

  environment {
    variables = merge(local.otel_common, {
      SPRING_CLOUD_FUNCTION_DEFINITION = "fraudAnalysisFunction"
      SPRING_PROFILES_ACTIVE           = "!local"
      AWS_REGION                       = var.aws_region
      BEDROCK_MODEL_ID                 = var.bedrock_model_id
      FRAUD_THRESHOLD                  = var.fraud_threshold
      OTEL_SERVICE_NAME                = "${var.project_name}-fraud-analysis"
    })
  }

  depends_on = [
    aws_iam_role_policy_attachment.lambda_basic,
    aws_iam_role_policy.lambda_dynamodb,
    aws_iam_role_policy.lambda_bedrock,
    aws_iam_role_policy.lambda_ssm
  ]
}

resource "aws_lambda_function" "compensation" {
  filename         = data.archive_file.lambda_jar.output_path
  function_name    = "${var.project_name}-compensation"
  role             = aws_iam_role.lambda_role.arn
  handler          = local.lambda_handler
  runtime          = "java21"
  source_code_hash = data.archive_file.lambda_jar.output_base64sha256
  memory_size      = var.lambda_memory
  timeout          = var.lambda_timeout
  architectures    = ["arm64"]
  publish          = true

  snap_start {
    apply_on = "PublishedVersions"
  }

  environment {
    variables = merge(local.otel_common, {
      SPRING_CLOUD_FUNCTION_DEFINITION = "compensationFunction"
      SPRING_PROFILES_ACTIVE           = "!local"
      AWS_REGION                       = var.aws_region
      OTEL_SERVICE_NAME                = "${var.project_name}-compensation"
    })
  }

  depends_on = [
    aws_iam_role_policy_attachment.lambda_basic,
    aws_iam_role_policy.lambda_dynamodb,
    aws_iam_role_policy.lambda_ssm
  ]
}

resource "aws_lambda_function" "update_status" {
  filename         = data.archive_file.lambda_jar.output_path
  function_name    = "${var.project_name}-update-status"
  role             = aws_iam_role.lambda_role.arn
  handler          = local.lambda_handler
  runtime          = "java21"
  source_code_hash = data.archive_file.lambda_jar.output_base64sha256
  memory_size      = var.lambda_memory
  timeout          = var.lambda_timeout
  architectures    = ["arm64"]
  publish          = true

  snap_start {
    apply_on = "PublishedVersions"
  }

  environment {
    variables = merge(local.otel_common, {
      SPRING_CLOUD_FUNCTION_DEFINITION = "updateStatusFunction"
      SPRING_PROFILES_ACTIVE           = "!local"
      AWS_REGION                       = var.aws_region
      FRAUD_THRESHOLD                  = var.fraud_threshold
      OTEL_SERVICE_NAME                = "${var.project_name}-update-status"
    })
  }

  depends_on = [
    aws_iam_role_policy_attachment.lambda_basic,
    aws_iam_role_policy.lambda_dynamodb,
    aws_iam_role_policy.lambda_ssm
  ]
}
