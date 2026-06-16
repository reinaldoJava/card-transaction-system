package com.empresa.cardtransactionsystem.adapters.outbound.noop;

import com.empresa.cardtransactionsystem.domain.model.SagaPayload;
import com.empresa.cardtransactionsystem.domain.ports.output.DomainEventPublisherPort;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("queue-none")
public class NoOpDomainEventPublisherAdapter implements DomainEventPublisherPort {

    @Override
    public void publish(SagaPayload payload) {}
}
