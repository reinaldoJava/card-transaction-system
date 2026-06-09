package com.empresa.cardtransactionsystem.adapters.outbound.kafka;

import com.empresa.cardtransactionsystem.domain.model.SagaPayload;
import com.empresa.cardtransactionsystem.domain.ports.output.DomainEventPublisherPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
@Profile("queue-kafka")
public class KafkaDomainEventPublisherAdapter implements DomainEventPublisherPort {

    private static final Logger log = LoggerFactory.getLogger(KafkaDomainEventPublisherAdapter.class);
    private static final String TOPIC = "card-transactions";

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public KafkaDomainEventPublisherAdapter(KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    @Override
    public void publish(SagaPayload payload) {
        log.info("Publishing transaction event to Kafka: correlationId={}", payload.correlationId());
        kafkaTemplate.send(TOPIC, payload.correlationId().toString(), payload);
    }
}
