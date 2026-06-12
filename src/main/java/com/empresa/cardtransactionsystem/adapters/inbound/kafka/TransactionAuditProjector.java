package com.empresa.cardtransactionsystem.adapters.inbound.kafka;

import com.empresa.cardtransactionsystem.domain.model.Brand;
import com.empresa.cardtransactionsystem.domain.model.CardToken;
import com.empresa.cardtransactionsystem.domain.model.SagaPayload;
import com.empresa.cardtransactionsystem.domain.model.TransactionStatus;
import com.empresa.cardtransactionsystem.domain.ports.output.AuditSearchPort;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Component
@Profile("queue-kafka")
public class TransactionAuditProjector {

    private static final Logger log = LoggerFactory.getLogger(TransactionAuditProjector.class);

    private final AuditSearchPort auditSearchPort;
    private final ObjectMapper objectMapper;

    public TransactionAuditProjector(AuditSearchPort auditSearchPort, ObjectMapper objectMapper) {
        this.auditSearchPort = auditSearchPort;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = "${kafka.topics.transactions:card-transactions}", groupId = "audit-projector")
    public void project(ConsumerRecord<String, String> record) {
        try {
            TransactionAuditEvent event = objectMapper.readValue(record.value(), TransactionAuditEvent.class);
            SagaPayload payload = event.toSagaPayload();
            auditSearchPort.index(payload);
            log.info("Indexed transaction {} into audit store", event.correlationId());
        } catch (Exception e) {
            log.error("Failed to project transaction audit event from offset {}: {}",
                    record.offset(), e.getMessage(), e);
        }
    }

    record TransactionAuditEvent(
            String transactionId,
            String correlationId,
            String cardToken,
            BigDecimal amount,
            int installments,
            String brand,
            String status,
            LocalDateTime createdAt
    ) {
        SagaPayload toSagaPayload() {
            return new SagaPayload(
                    transactionId,
                    UUID.fromString(correlationId),
                    new CardToken(cardToken),
                    amount,
                    installments,
                    Brand.valueOf(brand),
                    TransactionStatus.valueOf(status),
                    createdAt != null ? createdAt : LocalDateTime.now(),
                    null,
                    null,
                    null
            );
        }
    }
}
