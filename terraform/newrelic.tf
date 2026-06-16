provider "newrelic" {
  account_id = var.nr_account_id
  api_key    = var.nr_api_key
  region     = var.nr_region
}

# ─── Dashboard ───────────────────────────────────────────────────────────────

resource "newrelic_one_dashboard" "card_transaction" {
  name        = "${var.project_name} — Observability"
  permissions = "public_read_only"

  page {
    name = "Transactions"

    widget_billboard {
      title  = "Transactions Submitted (last 5m)"
      row    = 1
      column = 1
      width  = 4
      height = 3
      nrql_query {
        query = "SELECT rate(sum(transactions.submitted), 1 MINUTE) FROM Metric WHERE service.name LIKE '${var.project_name}%' SINCE 5 MINUTES AGO"
      }
    }

    widget_billboard {
      title  = "Approval Rate"
      row    = 1
      column = 5
      width  = 4
      height = 3
      nrql_query {
        query = <<-NRQL
          SELECT percentage(
            sum(transactions.completed),
            WHERE outcome = 'approved'
          ) FROM Metric
          WHERE service.name LIKE '${var.project_name}%'
          SINCE 30 MINUTES AGO
        NRQL
      }
      warning  = 90
      critical = 80
    }

    widget_billboard {
      title  = "Cache-Rejected (last 5m)"
      row    = 1
      column = 9
      width  = 4
      height = 3
      nrql_query {
        query = "SELECT sum(transactions.completed) FROM Metric WHERE outcome = 'rejected' AND reason = 'cache_fraud_score' AND service.name LIKE '${var.project_name}%' SINCE 5 MINUTES AGO"
      }
    }

    widget_line {
      title  = "Transaction Throughput"
      row    = 4
      column = 1
      width  = 6
      height = 3
      nrql_query {
        query = "SELECT rate(sum(transactions.submitted), 1 MINUTE) AS 'submitted', rate(sum(transactions.completed), 1 MINUTE) AS 'completed' FROM Metric WHERE service.name LIKE '${var.project_name}%' SINCE 1 HOUR AGO TIMESERIES"
      }
    }

    widget_line {
      title  = "Fraud Score Distribution (p50 / p90 / p99)"
      row    = 4
      column = 7
      width  = 6
      height = 3
      nrql_query {
        query = "SELECT percentile(fraud.score.distribution, 50, 90, 99) FROM Metric WHERE service.name = '${var.project_name}-fraud-analysis' SINCE 1 HOUR AGO TIMESERIES"
      }
    }
  }

  page {
    name = "Latency & Errors"

    widget_line {
      title  = "Saga Start Latency (p99)"
      row    = 1
      column = 1
      width  = 6
      height = 3
      nrql_query {
        query = "SELECT percentile(duration.ms, 99) FROM Span WHERE name = 'sfn.start-execution' AND service.name = '${var.project_name}-process-transaction' SINCE 1 HOUR AGO TIMESERIES"
      }
    }

    widget_line {
      title  = "Bedrock Fraud Analysis Latency (p50 / p99)"
      row    = 1
      column = 7
      width  = 6
      height = 3
      nrql_query {
        query = "SELECT percentile(duration.ms, 50, 99) FROM Span WHERE name = 'bedrock.fraud-analysis' AND service.name = '${var.project_name}-fraud-analysis' SINCE 1 HOUR AGO TIMESERIES"
      }
    }

    widget_line {
      title  = "DynamoDB Operation Latency (p99)"
      row    = 4
      column = 1
      width  = 6
      height = 3
      nrql_query {
        query = "SELECT percentile(duration.ms, 99) FROM Span WHERE name LIKE 'dynamodb.%' AND service.name LIKE '${var.project_name}%' FACET name SINCE 1 HOUR AGO TIMESERIES"
      }
    }

    widget_bar {
      title  = "Error Count by Lambda"
      row    = 4
      column = 7
      width  = 6
      height = 3
      nrql_query {
        query = "SELECT count(*) FROM Span WHERE otel.status_code = 'ERROR' AND service.name LIKE '${var.project_name}%' FACET service.name SINCE 1 HOUR AGO"
      }
    }
  }
}

# ─── Alert Policy ────────────────────────────────────────────────────────────

resource "newrelic_alert_policy" "card_transaction" {
  name                = "${var.project_name}-alerts"
  incident_preference = "PER_CONDITION"
}

resource "newrelic_nrql_alert_condition" "high_fraud_rejection_rate" {
  policy_id   = newrelic_alert_policy.card_transaction.id
  name        = "High fraud rejection rate"
  description = "More than 20% of completed transactions rejected by fraud analysis"
  enabled     = true

  nrql {
    query = <<-NRQL
      SELECT percentage(
        sum(transactions.completed),
        WHERE outcome = 'rejected' AND reason = 'fraud_analysis'
      ) FROM Metric
      WHERE service.name LIKE '${var.project_name}%'
    NRQL
  }

  critical {
    operator              = "above"
    threshold             = 20
    threshold_duration    = 300
    threshold_occurrences = "all"
  }

  warning {
    operator              = "above"
    threshold             = 10
    threshold_duration    = 300
    threshold_occurrences = "all"
  }

  fill_option        = "last_value"
  aggregation_window = 60
  aggregation_method = "event_flow"
  aggregation_delay  = 120
}

resource "newrelic_nrql_alert_condition" "bedrock_latency_spike" {
  policy_id   = newrelic_alert_policy.card_transaction.id
  name        = "Bedrock fraud analysis latency spike"
  description = "p99 latency of Bedrock calls exceeds threshold"
  enabled     = true

  nrql {
    query = "SELECT percentile(duration.ms, 99) FROM Span WHERE name = 'bedrock.fraud-analysis' AND service.name = '${var.project_name}-fraud-analysis'"
  }

  critical {
    operator              = "above"
    threshold             = 15000
    threshold_duration    = 300
    threshold_occurrences = "all"
  }

  warning {
    operator              = "above"
    threshold             = 10000
    threshold_duration    = 300
    threshold_occurrences = "all"
  }

  fill_option        = "last_value"
  aggregation_window = 60
  aggregation_method = "event_flow"
  aggregation_delay  = 120
}

resource "newrelic_nrql_alert_condition" "saga_errors" {
  policy_id   = newrelic_alert_policy.card_transaction.id
  name        = "Saga start errors"
  description = "Any error on saga.start span"
  enabled     = true

  nrql {
    query = "SELECT count(*) FROM Span WHERE name = 'sfn.start-execution' AND otel.status_code = 'ERROR' AND service.name = '${var.project_name}-process-transaction'"
  }

  critical {
    operator              = "above"
    threshold             = 0
    threshold_duration    = 60
    threshold_occurrences = "at_least_once"
  }

  fill_option        = "none"
  aggregation_window = 60
  aggregation_method = "event_flow"
  aggregation_delay  = 60
}

resource "newrelic_nrql_alert_condition" "low_throughput" {
  policy_id   = newrelic_alert_policy.card_transaction.id
  name        = "Transaction throughput drop"
  description = "Transaction submission rate drops to zero for 5 minutes"
  enabled     = true

  nrql {
    query = "SELECT rate(sum(transactions.submitted), 1 MINUTE) FROM Metric WHERE service.name LIKE '${var.project_name}%'"
  }

  critical {
    operator              = "equals"
    threshold             = 0
    threshold_duration    = 300
    threshold_occurrences = "all"
  }

  fill_option        = "last_value"
  aggregation_window = 60
  aggregation_method = "event_flow"
  aggregation_delay  = 120
}
