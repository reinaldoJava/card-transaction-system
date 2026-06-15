package com.empresa.cardtransactionsystem.adapters.inbound.kafka;

import com.empresa.cardtransactionsystem.adapters.inbound.kafka.dto.TransactionAuditEvent;
import com.empresa.cardtransactionsystem.domain.ports.output.AuditSearchPort;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

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
            auditSearchPort.index(event.toSagaPayload());
            log.info("Indexed transaction {} into audit store", event.correlationId());
        } catch (Exception e) {
            log.error("Failed to project transaction audit event from offset {}: {}",
                    record.offset(), e.getMessage(), e);
        }
    }
}
