package com.empresa.cardtransactionsystem.domain.ports.output;

import com.empresa.cardtransactionsystem.domain.model.SagaPayload;

public interface DomainEventPublisherPort {
    void publish(SagaPayload payload);
}
