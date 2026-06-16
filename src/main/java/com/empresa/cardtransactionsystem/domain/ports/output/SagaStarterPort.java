package com.empresa.cardtransactionsystem.domain.ports.output;

import com.empresa.cardtransactionsystem.domain.model.SagaPayload;

public interface SagaStarterPort {
    void start(SagaPayload payload);
}
