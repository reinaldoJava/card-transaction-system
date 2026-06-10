package com.empresa.cardtransactionsystem.adapters.outbound.kafka;

import com.empresa.cardtransactionsystem.domain.model.Brand;
import com.empresa.cardtransactionsystem.domain.model.CardToken;
import com.empresa.cardtransactionsystem.domain.model.SagaPayload;
import com.empresa.cardtransactionsystem.domain.model.TransactionStatus;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.serializer.JacksonJsonSerializer;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("integration")
class KafkaDomainEventPublisherAdapterTest {

    private static final String BOOTSTRAP_SERVERS = "localhost:19092";

    private KafkaDomainEventPublisherAdapter adapter;
    private KafkaConsumer<String, String> consumer;

    @BeforeEach
    void setUp() {
        Map<String, Object> producerConfig = Map.of(
                "bootstrap.servers", BOOTSTRAP_SERVERS,
                "key.serializer", StringSerializer.class,
                "value.serializer", JacksonJsonSerializer.class
        );
        KafkaTemplate<String, String> kafkaTemplate =
                new KafkaTemplate<>(new DefaultKafkaProducerFactory<>(producerConfig));
        adapter = new KafkaDomainEventPublisherAdapter(kafkaTemplate);

        consumer = new KafkaConsumer<>(Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, BOOTSTRAP_SERVERS,
                ConsumerConfig.GROUP_ID_CONFIG, "test-group-" + UUID.randomUUID(),
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest",
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class
        ));
        consumer.subscribe(Collections.singletonList("card-transactions"));
    }

    @AfterEach
    void tearDown() {
        consumer.close();
    }

    @Test
    void shouldPublishPayloadToKafkaWithCorrelationIdAsKey() {
        UUID correlationId = UUID.randomUUID();
        SagaPayload payload = new SagaPayload(
                "TX-KAFKA-001",
                correlationId,
                new CardToken("token-abc"),
                new BigDecimal("750.00"),
                3,
                Brand.VISA,
                TransactionStatus.PENDING,
                LocalDateTime.now(),
                null, null
        );

        adapter.publish(payload);

        List<ConsumerRecord<String, String>> records = pollRecords(Duration.ofSeconds(10));

        assertThat(records).hasSize(1);
        assertThat(records.get(0).key()).isEqualTo(correlationId.toString());
        assertThat(records.get(0).value()).contains("TX-KAFKA-001");
        assertThat(records.get(0).value()).contains(correlationId.toString());
    }

    @Test
    void shouldPublishMultipleEventsInOrder() {
        UUID id1 = UUID.randomUUID();
        UUID id2 = UUID.randomUUID();

        adapter.publish(buildPayload("TX-001", id1));
        adapter.publish(buildPayload("TX-002", id2));

        List<ConsumerRecord<String, String>> records = pollRecords(Duration.ofSeconds(10));

        assertThat(records).hasSize(2);
        assertThat(records.get(0).key()).isEqualTo(id1.toString());
        assertThat(records.get(1).key()).isEqualTo(id2.toString());
    }

    private SagaPayload buildPayload(String transactionId, UUID correlationId) {
        return new SagaPayload(
                transactionId, correlationId, new CardToken("token"),
                new BigDecimal("100.00"), 1, Brand.MASTER,
                TransactionStatus.PENDING, LocalDateTime.now(), null, null
        );
    }

    private List<ConsumerRecord<String, String>> pollRecords(Duration timeout) {
        long deadline = System.currentTimeMillis() + timeout.toMillis();
        while (System.currentTimeMillis() < deadline) {
            var batch = consumer.poll(Duration.ofMillis(500));
            if (!batch.isEmpty()) {
                List<ConsumerRecord<String, String>> result = new ArrayList<>();
                batch.records("card-transactions").forEach(result::add);
                return result;
            }
        }
        return List.of();
    }
}
