package com.empresa.cardtransactionsystem.domain.ports.input;

import com.empresa.cardtransactionsystem.domain.model.SagaPayload;
import com.empresa.cardtransactionsystem.domain.model.ValidationResult;

public interface ValidateBusinessRulesUseCase {
    ValidationResult validate(SagaPayload payload);
}
