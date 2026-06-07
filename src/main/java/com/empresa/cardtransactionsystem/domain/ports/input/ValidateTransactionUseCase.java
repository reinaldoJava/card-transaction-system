package com.empresa.cardtransactionsystem.domain.ports.input;

import com.empresa.cardtransactionsystem.domain.model.SagaPayload;

public interface ValidateTransactionUseCase {
    void validate(SagaPayload payload);
}
