package com.empresa.cardtransactionsystem.application.usecase;

import com.empresa.cardtransactionsystem.domain.model.ClientProfile;
import com.empresa.cardtransactionsystem.domain.model.SagaPayload;
import com.empresa.cardtransactionsystem.domain.model.ValidationResult;
import com.empresa.cardtransactionsystem.domain.ports.input.ValidateBusinessRulesUseCase;
import com.empresa.cardtransactionsystem.domain.ports.output.CachePort;
import com.empresa.cardtransactionsystem.domain.ports.output.ClientProfilePort;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.Optional;

@Service
public class ValidateBusinessRulesService implements ValidateBusinessRulesUseCase {

    private final CachePort cachePort;
    private final ClientProfilePort clientProfilePort;

    public ValidateBusinessRulesService(CachePort cachePort, ClientProfilePort clientProfilePort) {
        this.cachePort = cachePort;
        this.clientProfilePort = clientProfilePort;
    }

    @Override
    public ValidationResult validate(SagaPayload payload) {
        ClientProfile profile = resolveClientProfile(payload);
        return validateInstallments(payload.installments(), profile)
                .orElseGet(() -> validateCreditLimit(payload.amount(), payload.installments(), profile));
    }

    private Optional<ValidationResult> validateInstallments(int installments, ClientProfile profile) {
        if (installments < 1 || installments > profile.maxInstallments()) {
            return Optional.of(ValidationResult.invalid(
                    "Invalid installments: %d. Must be between 1 and %d."
                            .formatted(installments, profile.maxInstallments())));
        }
        return Optional.empty();
    }

    private ValidationResult validateCreditLimit(BigDecimal amount, int installments, ClientProfile profile) {
        BigDecimal totalAmount = amount
                .multiply(BigDecimal.ONE.add(profile.monthlyRate()).pow(installments, MathContext.DECIMAL64))
                .setScale(2, RoundingMode.HALF_UP);
        if (!profile.hasAvailableCredit(totalAmount)) {
            return ValidationResult.invalid(
                    "Insufficient credit. Available: %s, Required: %s."
                            .formatted(profile.availableCredit(), totalAmount));
        }
        return ValidationResult.valid();
    }

    private ClientProfile resolveClientProfile(SagaPayload payload) {
        return cachePort.getClientProfile(payload.cardToken())
                .or(() -> clientProfilePort.findByCardToken(payload.cardToken()))
                .orElseThrow(() -> new IllegalStateException(
                        "ClientProfile not found for card token: " + payload.cardToken().value()));
    }
}
