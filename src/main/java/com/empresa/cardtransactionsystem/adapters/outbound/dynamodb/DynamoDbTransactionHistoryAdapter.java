package com.empresa.cardtransactionsystem.adapters.outbound.dynamodb;

import com.empresa.cardtransactionsystem.adapters.outbound.dynamodb.entity.CardTransactionDdbEntity;
import com.empresa.cardtransactionsystem.domain.model.CardToken;
import com.empresa.cardtransactionsystem.domain.model.TransactionHistory;
import com.empresa.cardtransactionsystem.domain.model.TransactionSummary;
import com.empresa.cardtransactionsystem.domain.ports.output.TransactionHistoryPort;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbIndex;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryConditional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Repository
@Profile("ledger-dynamodb")
public class DynamoDbTransactionHistoryAdapter implements TransactionHistoryPort {

    private final DynamoDbIndex<CardTransactionDdbEntity> cardTokenIndex;

    public DynamoDbTransactionHistoryAdapter(DynamoDbEnhancedClient enhancedClient) {
        DynamoDbTable<CardTransactionDdbEntity> table = enhancedClient.table(
                "card-transactions", TableSchema.fromBean(CardTransactionDdbEntity.class));
        this.cardTokenIndex = table.index("cardToken-index");
    }

    @Override
    public TransactionHistory findByCardToken(CardToken cardToken) {
        QueryConditional condition = QueryConditional.keyEqualTo(
                Key.builder().partitionValue(cardToken.value()).build());

        List<CardTransactionDdbEntity> items = cardTokenIndex
                .query(r -> r.queryConditional(condition))
                .stream()
                .flatMap(page -> page.items().stream())
                .toList();

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime cutoff24h = now.minusHours(24);
        LocalDateTime cutoff30d = now.minusDays(30);

        int velocityLast24h = 0;
        BigDecimal totalAmountLast30Days = BigDecimal.ZERO;

        for (CardTransactionDdbEntity e : items) {
            LocalDateTime createdAt = LocalDateTime.parse(e.getCreatedAt());
            if (createdAt.isAfter(cutoff24h)) velocityLast24h++;
            if (createdAt.isAfter(cutoff30d)) totalAmountLast30Days = totalAmountLast30Days.add(e.getAmount());
        }

        List<TransactionSummary> recent = items.stream()
                .sorted((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()))
                .limit(10)
                .map(e -> new TransactionSummary(
                        UUID.fromString(e.getUuidTransaction()),
                        e.getAmount(),
                        com.empresa.cardtransactionsystem.domain.model.TransactionStatus.valueOf(e.getStatus()),
                        LocalDateTime.parse(e.getCreatedAt())))
                .toList();

        return new TransactionHistory(recent, velocityLast24h, totalAmountLast30Days);
    }
}
