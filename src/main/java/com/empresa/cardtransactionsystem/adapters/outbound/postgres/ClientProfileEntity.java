package com.empresa.cardtransactionsystem.adapters.outbound.postgres;

import com.empresa.cardtransactionsystem.domain.model.ClientProfile;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;

@Entity
@Table(name = "client_profiles")
public class ClientProfileEntity {

    @Id
    @Column(name = "card_token")
    private String cardToken;

    @Column(name = "credit_limit", precision = 19, scale = 4, nullable = false)
    private BigDecimal creditLimit;

    @Column(name = "used_credit", precision = 19, scale = 4, nullable = false)
    private BigDecimal usedCredit;

    @Column(name = "max_installments", nullable = false)
    private int maxInstallments;

    @Column(name = "monthly_rate", precision = 5, scale = 4, nullable = false)
    private BigDecimal monthlyRate;

    public static ClientProfileEntity from(ClientProfile profile, String cardToken) {
        ClientProfileEntity e = new ClientProfileEntity();
        e.cardToken = cardToken;
        e.creditLimit = profile.creditLimit();
        e.usedCredit = profile.usedCredit();
        e.maxInstallments = profile.maxInstallments();
        e.monthlyRate = profile.monthlyRate();
        return e;
    }

    public ClientProfile toDomain() {
        return new ClientProfile(creditLimit, usedCredit, maxInstallments, monthlyRate);
    }

    public String getCardToken() { return cardToken; }
    public void setCardToken(String cardToken) { this.cardToken = cardToken; }
}
