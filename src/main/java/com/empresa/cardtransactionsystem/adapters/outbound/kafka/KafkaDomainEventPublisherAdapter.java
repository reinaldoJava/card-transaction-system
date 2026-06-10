package com.empresa.cardtransactionsystem.adapters.outbound.kafka;

import com.empresa.cardtransactionsystem.domain.model.SagaPayload;
import com.empresa.cardtransactionsystem.domain.ports.output.DomainEventPublisherPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
@Profile("local-rich")
public class KafkaDomainEventPublisherAdapter implements DomainEventPublisherPort {

    private static final Logger log = LoggerFactory.getLogger(KafkaDomainEventPublisherAdapter.class);
    private static final String TOPIC = "card-transactions";

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public KafkaDomainEventPublisherAdapter(KafkaTemplate<String, String> kafkaTemplate,
                                             ObjectMapper objectMapper) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public void publish(SagaPayload payload) {
        try {
            TransactionEvent event = TransactionEvent.from(payload);
            String json = objectMapper.writeValueAsString(event);
            log.info("Publishing transaction event to Kafka: correlationId={}", payload.correlationId());
            kafkaTemplate.send(TOPIC, payload.correlationId().toString(), json);
        } catch (Exception e) {
            log.error("Failed to serialize transaction event for correlationId={}: {}",
                    payload.correlationId(), e.getMessage(), e);
        }
    }

    record TransactionEvent(
            String transactionId,
            String correlationId,
            String cardToken,
            java.math.BigDecimal amount,
            int installments,
            String brand,
            String status,
            java.time.LocalDateTime createdAt
    ) {
        static TransactionEvent from(SagaPayload p) {
            return new TransactionEvent(
                    p.transactionId(),
                    p.correlationId().toString(),
                    p.cardToken().value(),
                    p.amount(),
                    p.installments(),
                    p.brand().name(),
                    p.status().name(),
                    p.createdAt()
            );
        }
    }
}
