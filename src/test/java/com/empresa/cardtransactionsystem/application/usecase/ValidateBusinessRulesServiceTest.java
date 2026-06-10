package com.empresa.cardtransactionsystem.application.usecase;

import com.empresa.cardtransactionsystem.domain.model.Brand;
import com.empresa.cardtransactionsystem.domain.model.CardToken;
import com.empresa.cardtransactionsystem.domain.model.ClientProfile;
import com.empresa.cardtransactionsystem.domain.model.SagaPayload;
import com.empresa.cardtransactionsystem.domain.model.TransactionStatus;
import com.empresa.cardtransactionsystem.domain.model.ValidationResult;
import com.empresa.cardtransactionsystem.domain.ports.output.CachePort;
import com.empresa.cardtransactionsystem.domain.ports.output.ClientProfilePort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("ValidateBusinessRulesService")
class ValidateBusinessRulesServiceTest {

    @Mock private CachePort cachePort;
    @Mock private ClientProfilePort clientProfilePort;

    private ValidateBusinessRulesService service;

    private static final CardToken TOKEN = new CardToken("test-token-uuid");
    private static final ClientProfile PROFILE = new ClientProfile(
            new BigDecimal("10000.00"), new BigDecimal("0.00"), 24, new BigDecimal("0.01"), false);

    @BeforeEach
    void setUp() {
        service = new ValidateBusinessRulesService(cachePort, clientProfilePort);
        lenient().when(cachePort.getClientProfile(TOKEN)).thenReturn(Optional.empty());
        lenient().when(clientProfilePort.findByCardToken(TOKEN)).thenReturn(Optional.of(PROFILE));
    }

    @Test
    @DisplayName("should approve transaction within limits")
    void shouldApproveTransactionWithinLimits() {
        assertThat(service.validate(payload("500.00", 3)).approved()).isTrue();
    }

    @Test
    @DisplayName("should reject when installments exceed maximum")
    void shouldRejectWhenInstallmentsExceedMax() {
        ValidationResult result = service.validate(payload("500.00", 25));
        assertThat(result.approved()).isFalse();
        assertThat(result.reason()).contains("installments");
    }

    @Test
    @DisplayName("should reject when total amount exceeds available credit")
    void shouldRejectWhenAmountExceedsAvailableCredit() {
        ClientProfile limited = new ClientProfile(
                new BigDecimal("100.00"), new BigDecimal("90.00"), 24, new BigDecimal("0.01"), false);
        when(clientProfilePort.findByCardToken(TOKEN)).thenReturn(Optional.of(limited));
        ValidationResult result = service.validate(payload("50.00", 1));
        assertThat(result.approved()).isFalse();
        assertThat(result.reason()).contains("credit");
    }

    @Test
    @DisplayName("should use cached client profile when available")
    void shouldUseCachedClientProfile() {
        when(cachePort.getClientProfile(TOKEN)).thenReturn(Optional.of(PROFILE));
        assertThat(service.validate(payload("500.00", 1)).approved()).isTrue();
    }

    @Test
    @DisplayName("should approve with maximum allowed installments")
    void shouldApproveWithMaxInstallments() {
        assertThat(service.validate(payload("500.00", 24)).approved()).isTrue();
    }

    private SagaPayload payload(String amount, int installments) {
        return new SagaPayload("TXN-001", UUID.randomUUID(), TOKEN,
                new BigDecimal(amount), installments, Brand.VISA,
                TransactionStatus.PENDING, LocalDateTime.now(), null, null);
    }
}
