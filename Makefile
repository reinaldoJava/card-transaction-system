.PHONY: help localstack-up localstack-down localstack-logs localstack-reset \
        app-run app-test app-build clean aws-list-tables aws-scan

help:
	@echo "Card Transaction System - Commands"
	@echo "===================================="
	@echo ""
	@echo "LocalStack:"
	@echo "  make localstack-up       - Start LocalStack with DynamoDB"
	@echo "  make localstack-down     - Stop LocalStack"
	@echo "  make localstack-reset    - Stop and remove all LocalStack data"
	@echo "  make localstack-logs     - Show LocalStack logs"
	@echo ""
	@echo "Application:"
	@echo "  make app-build           - Build the application"
	@echo "  make app-run             - Run app with LocalStack (local profile)"
	@echo "  make app-test            - Run all tests"
	@echo ""
	@echo "AWS CLI (requires LocalStack running):"
	@echo "  make aws-list-tables     - List all DynamoDB tables"
	@echo "  make aws-scan TABLE=name - Scan a table (e.g., TABLE=card-transactions)"
	@echo ""
	@echo "Cleanup:"
	@echo "  make clean               - Clean build artifacts"

localstack-up:
	@echo "Starting LocalStack with DynamoDB..."
	docker-compose up -d
	@echo "Waiting for LocalStack to initialize..."
	sleep 10
	@echo "✓ LocalStack is running on http://localhost:4566"

localstack-down:
	@echo "Stopping LocalStack..."
	docker-compose down
	@echo "✓ LocalStack stopped"

localstack-reset:
	@echo "Resetting LocalStack (removing all data)..."
	docker-compose down -v
	@echo "✓ LocalStack reset complete"

localstack-logs:
	docker-compose logs -f localstack

app-build:
	@echo "Building application..."
	./mvnw clean compile -DskipTests
	@echo "✓ Build complete"

app-run: localstack-up
	@echo "Starting application with local profile..."
	./mvnw spring-boot:run -Dspring-boot.run.arguments="--spring.profiles.active=local"

app-test: localstack-up
	@echo "Running tests..."
	./mvnw test
	@echo "✓ Tests complete"

aws-list-tables:
	aws dynamodb list-tables \
	  --endpoint-url http://localhost:4566 \
	  --region us-east-1 \
	  --query 'TableNames' \
	  --output table

aws-scan:
	@if [ -z "$(TABLE)" ]; then \
		echo "Usage: make aws-scan TABLE=table-name"; \
		exit 1; \
	fi
	aws dynamodb scan \
	  --table-name $(TABLE) \
	  --endpoint-url http://localhost:4566 \
	  --region us-east-1 \
	  --output table

clean:
	@echo "Cleaning build artifacts..."
	./mvnw clean
	rm -rf target/
	@echo "✓ Clean complete"

.DEFAULT_GOAL := help
