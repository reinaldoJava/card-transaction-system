package com.empresa.cardtransactionsystem.adapters.outbound.dynamodb.entity;

import com.empresa.cardtransactionsystem.domain.model.auth.User;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;

@DynamoDbBean
public class UserDdbEntity {
    private String username;
    private String hashedPassword;

    @DynamoDbPartitionKey
    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getHashedPassword() {
        return hashedPassword;
    }

    public void setHashedPassword(String hashedPassword) {
        this.hashedPassword = hashedPassword;
    }

    public User toDomain() {
        return new User(username, hashedPassword);
    }

    public static UserDdbEntity fromDomain(User domain) {
        UserDdbEntity entity = new UserDdbEntity();
        entity.setUsername(domain.username());
        entity.setHashedPassword(domain.hashedPassword());
        return entity;
    }
}
