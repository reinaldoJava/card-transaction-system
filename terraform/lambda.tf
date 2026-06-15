data "archive_file" "lambda_jar" {
  type        = "zip"
  source_file = local.jar_path
  output_path = "${path.module}/../target/lambda.zip"
}

locals {
  otel_common = {
    NR_LICENSE_KEY_SSM_PATH     = aws_ssm_parameter.nr_license_key.name
    OTEL_EXPORTER_OTLP_ENDPOINT = var.otel_exporter_endpoint
  }
}

resource "aws_lambda_function" "process_transaction" {
  filename         = data.archive_file.lambda_jar.output_path
  function_name    = "${var.project_name}-process-transaction"
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
      SPRING_CLOUD_FUNCTION_DEFINITION = "processTransactionFunction"
      SPRING_PROFILES_ACTIVE           = "!local"
      AWS_REGION                       = var.aws_region
      JWT_SECRET_SSM_PATH              = aws_ssm_parameter.jwt_secret.name
      JWT_EXPIRATION_HOURS             = var.jwt_expiration_hours
      STEP_FUNCTIONS_STATE_MACHINE_ARN = aws_sfn_state_machine.transaction_saga.arn
      FRAUD_THRESHOLD                  = var.fraud_threshold
      OTEL_SERVICE_NAME                = "${var.project_name}-process-transaction"
    })
  }

  depends_on = [
    aws_iam_role_policy_attachment.lambda_basic,
    aws_iam_role_policy.lambda_dynamodb,
    aws_iam_role_policy.lambda_step_functions,
    aws_iam_role_policy.lambda_ssm
  ]
}

resource "aws_lambda_function_url" "process_transaction_url" {
  function_name      = aws_lambda_function.process_transaction.function_name
  qualifier          = aws_lambda_function.process_transaction.version
  authorization_type = "NONE"
  cors {
    allow_headers = ["Content-Type", "Authorization"]
    allow_methods = ["POST", "GET", "OPTIONS"]
    allow_origins = var.allowed_origins
    max_age       = 86400
  }
}

resource "aws_lambda_function" "login" {
  filename         = data.archive_file.lambda_jar.output_path
  function_name    = "${var.project_name}-login"
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
      SPRING_CLOUD_FUNCTION_DEFINITION = "loginFunction"
      SPRING_PROFILES_ACTIVE           = "!local"
      AWS_REGION                       = var.aws_region
      JWT_SECRET_SSM_PATH              = aws_ssm_parameter.jwt_secret.name
      JWT_EXPIRATION_HOURS             = var.jwt_expiration_hours
      OTEL_SERVICE_NAME                = "${var.project_name}-login"
    })
  }

  depends_on = [
    aws_iam_role_policy_attachment.lambda_basic,
    aws_iam_role_policy.lambda_dynamodb,
    aws_iam_role_policy.lambda_ssm
  ]
}

resource "aws_lambda_function_url" "login_url" {
  function_name      = aws_lambda_function.login.function_name
  qualifier          = aws_lambda_function.login.version
  authorization_type = "NONE"
  cors {
    allow_headers = ["Content-Type", "Authorization"]
    allow_methods = ["POST", "OPTIONS"]
    allow_origins = var.allowed_origins
    max_age       = 86400
  }
}

resource "aws_lambda_function" "token_exchange" {
  filename         = data.archive_file.lambda_jar.output_path
  function_name    = "${var.project_name}-token-exchange"
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
      SPRING_CLOUD_FUNCTION_DEFINITION = "tokenExchange"
      SPRING_PROFILES_ACTIVE           = "!local"
      AWS_REGION                       = var.aws_region
      JWT_SECRET_SSM_PATH              = aws_ssm_parameter.jwt_secret.name
      JWT_EXPIRATION_HOURS             = var.jwt_expiration_hours
      OTEL_SERVICE_NAME                = "${var.project_name}-token-exchange"
    })
  }

  depends_on = [
    aws_iam_role_policy_attachment.lambda_basic,
    aws_iam_role_policy.lambda_ssm
  ]
}

resource "aws_lambda_function_url" "token_exchange_url" {
  function_name      = aws_lambda_function.token_exchange.function_name
  qualifier          = aws_lambda_function.token_exchange.version
  authorization_type = "NONE"
  cors {
    allow_headers = ["Content-Type", "Authorization"]
    allow_methods = ["POST", "OPTIONS"]
    allow_origins = var.allowed_origins
    max_age       = 86400
  }
}

resource "aws_lambda_function" "get_status" {
  filename         = data.archive_file.lambda_jar.output_path
  function_name    = "${var.project_name}-get-status"
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
      SPRING_CLOUD_FUNCTION_DEFINITION = "getStatusFunction"
      SPRING_PROFILES_ACTIVE           = "!local"
      AWS_REGION                       = var.aws_region
      JWT_SECRET_SSM_PATH              = aws_ssm_parameter.jwt_secret.name
      JWT_EXPIRATION_HOURS             = var.jwt_expiration_hours
      OTEL_SERVICE_NAME                = "${var.project_name}-get-status"
    })
  }

  depends_on = [
    aws_iam_role_policy_attachment.lambda_basic,
    aws_iam_role_policy.lambda_dynamodb,
    aws_iam_role_policy.lambda_ssm
  ]
}

resource "aws_lambda_function_url" "get_status_url" {
  function_name      = aws_lambda_function.get_status.function_name
  qualifier          = aws_lambda_function.get_status.version
  authorization_type = "NONE"
  cors {
    allow_headers = ["Content-Type", "Authorization"]
    allow_methods = ["GET", "OPTIONS"]
    allow_origins = var.allowed_origins
    max_age       = 86400
  }
}
