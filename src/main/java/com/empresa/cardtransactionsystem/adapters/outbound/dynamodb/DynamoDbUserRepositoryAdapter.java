package com.empresa.cardtransactionsystem.adapters.outbound.dynamodb;

import com.empresa.cardtransactionsystem.domain.model.auth.User;
import com.empresa.cardtransactionsystem.domain.ports.output.UserRepositoryPort;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public class DynamoDbUserRepositoryAdapter implements UserRepositoryPort {

    private final DynamoDbTable<UserDdbEntity> userTable;

    public DynamoDbUserRepositoryAdapter(DynamoDbEnhancedClient enhancedClient) {
        this.userTable = enhancedClient.table("users",
                TableSchema.fromBean(UserDdbEntity.class));
    }

    @Override
    public Optional<User> findByUsername(String username) {
        UserDdbEntity entity = userTable.getItem(
                r -> r.key(k -> k.partitionValue(username))
        );
        return Optional.ofNullable(entity)
                .map(UserDdbEntity::toDomain);
    }
}
