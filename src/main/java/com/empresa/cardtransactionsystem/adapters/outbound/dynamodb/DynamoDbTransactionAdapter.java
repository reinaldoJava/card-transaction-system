package com.empresa.cardtransactionsystem.adapters.outbound.dynamodb;

import com.empresa.cardtransactionsystem.domain.model.SagaPayload;
import com.empresa.cardtransactionsystem.domain.model.TransactionStatus;
import com.empresa.cardtransactionsystem.domain.ports.output.TransactionRepositoryPort;
import io.micrometer.observation.annotation.Observed;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.model.UpdateItemEnhancedRequest;

import java.util.Optional;
import java.util.UUID;

@Repository
public class DynamoDbTransactionAdapter implements TransactionRepositoryPort {

    private final DynamoDbTable<CardTransactionDdbEntity> transactionTable;

    public DynamoDbTransactionAdapter(DynamoDbEnhancedClient enhancedClient) {
        this.transactionTable = enhancedClient.table("card-transactions",
                TableSchema.fromBean(CardTransactionDdbEntity.class));
    }

    @Override
    @Observed(name = "db.transaction.save", contextualName = "dynamodb.put-transaction")
    public void save(SagaPayload payload) {
        transactionTable.putItem(CardTransactionDdbEntity.from(payload));
    }

    @Override
    @Observed(name = "db.transaction.update_status", contextualName = "dynamodb.update-transaction-status")
    public void updateStatus(UUID correlationId, TransactionStatus status) {
        CardTransactionDdbEntity entity = new CardTransactionDdbEntity();
        entity.setUuidTransaction(correlationId.toString());
        entity.setStatus(status.name());
        transactionTable.updateItem(
                UpdateItemEnhancedRequest.builder(CardTransactionDdbEntity.class)
                        .item(entity)
                        .build()
        );
    }

    @Override
    @Observed(name = "db.transaction.find_status", contextualName = "dynamodb.get-transaction-status")
    public Optional<TransactionStatus> findStatus(UUID correlationId) {
        CardTransactionDdbEntity entity = transactionTable.getItem(
                r -> r.key(k -> k.partitionValue(correlationId.toString()))
        );
        return Optional.ofNullable(entity)
                .map(e -> TransactionStatus.valueOf(e.getStatus()));
    }
}
