package com.empresa.cardtransactionsystem.adapters.outbound.lambda;

import io.jsonwebtoken.Jwts;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.time.Instant;
import java.util.Date;

@Component
public class JwtGenerator {

    private final SecretKey key;
    private final long expirationHours;

    public JwtGenerator(
            SecretKey jwtSecretKey,
            @Value("${jwt.expiration-hours:24}") long expirationHours) {
        this.key = jwtSecretKey;
        this.expirationHours = expirationHours;
    }

    public String generate(String username) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(username)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(expirationHours * 3600)))
                .signWith(key)
                .compact();
    }
}
