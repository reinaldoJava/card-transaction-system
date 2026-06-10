#!/bin/bash
set -e

PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
TERRAFORM_DIR="$PROJECT_ROOT/terraform"

echo "========================================"
echo "Card Transaction System - AWS Deploy"
echo "========================================"
echo ""

# Step 1: Build JAR
echo "Step 1: Building JAR..."
cd "$PROJECT_ROOT"
mvn clean package -DskipTests || {
    echo "❌ Build failed!"
    exit 1
}
echo "✅ JAR built successfully"
echo ""

# Step 2: Terraform Init
echo "Step 2: Initializing Terraform..."
cd "$TERRAFORM_DIR"
terraform init || {
    echo "❌ Terraform init failed!"
    exit 1
}
echo "✅ Terraform initialized"
echo ""

# Step 3: Terraform Plan
echo "Step 3: Planning Terraform..."
terraform plan -out=tfplan || {
    echo "❌ Terraform plan failed!"
    exit 1
}
echo "✅ Plan created"
echo ""

# Step 4: Confirm
echo "Step 4: Confirming deployment..."
read -p "Do you want to apply this plan? (yes/no): " confirm
if [ "$confirm" != "yes" ]; then
    echo "❌ Deployment cancelled"
    rm tfplan
    exit 1
fi
echo ""

# Step 5: Terraform Apply
echo "Step 5: Applying infrastructure..."
terraform apply tfplan || {
    echo "❌ Terraform apply failed!"
    exit 1
}
echo "✅ Infrastructure deployed successfully!"
echo ""

# Step 6: Output URLs
echo "Step 6: Deployment Summary"
echo "========================================"
terraform output
echo "========================================"
echo ""
echo "✅ Deployment complete!"
echo ""
echo "Next steps:"
echo "1. Test endpoints with curl:"
echo "   curl -X POST https://<process_transaction_url>"
echo ""
echo "2. Monitor logs:"
echo "   aws logs tail /aws/lambda/card-transaction-system-process-transaction --follow"
echo ""
echo "3. Check SQS metrics:"
echo "   aws sqs get-queue-attributes --queue-url <validation_queue_url> --attribute-names All"
echo ""
