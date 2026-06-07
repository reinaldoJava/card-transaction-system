package com.empresa.cardtransactionsystem.adapters.outbound.dynamodb;

import com.empresa.cardtransactionsystem.domain.model.CardToken;
import com.empresa.cardtransactionsystem.domain.model.ClientProfile;
import com.empresa.cardtransactionsystem.domain.ports.output.ClientProfilePort;
import io.micrometer.observation.annotation.Observed;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;

import java.util.Optional;

@Repository
public class DynamoDbClientProfileAdapter implements ClientProfilePort {

    private final DynamoDbTable<ClientProfileDdbEntity> profileTable;

    public DynamoDbClientProfileAdapter(DynamoDbEnhancedClient enhancedClient) {
        this.profileTable = enhancedClient.table("client-profiles",
                TableSchema.fromBean(ClientProfileDdbEntity.class));
    }

    @Override
    @Observed(name = "db.client_profile.find", contextualName = "dynamodb.get-client-profile")
    public Optional<ClientProfile> findByCardToken(CardToken cardToken) {
        ClientProfileDdbEntity entity = profileTable.getItem(
                r -> r.key(k -> k.partitionValue(cardToken.value()))
        );
        return Optional.ofNullable(entity)
                .map(ClientProfileDdbEntity::toDomain);
    }
}
