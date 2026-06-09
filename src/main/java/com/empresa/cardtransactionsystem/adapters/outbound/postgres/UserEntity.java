package com.empresa.cardtransactionsystem.adapters.outbound.postgres;

import com.empresa.cardtransactionsystem.domain.model.auth.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "users")
public class UserEntity {

    @Id
    @Column(name = "username")
    private String username;

    @Column(name = "hashed_password", nullable = false)
    private String hashedPassword;

    public static UserEntity from(User user) {
        UserEntity e = new UserEntity();
        e.username = user.username();
        e.hashedPassword = user.hashedPassword();
        return e;
    }

    public User toDomain() {
        return new User(username, hashedPassword);
    }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getHashedPassword() { return hashedPassword; }
    public void setHashedPassword(String hashedPassword) { this.hashedPassword = hashedPassword; }
}
