package com.empresa.cardtransactionsystem.adapters.outbound.kafka;

import com.empresa.cardtransactionsystem.domain.model.CardToken;
import com.empresa.cardtransactionsystem.domain.model.SagaPayload;
import com.empresa.cardtransactionsystem.fixture.SagaPayloadFixture;
import tools.jackson.databind.ObjectMapper;
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

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("integration")
class KafkaDomainEventPublisherAdapterTest {

    private static final String BOOTSTRAP_SERVERS = "localhost:19092";
    private static final String TOPIC = "card-transactions";

    private KafkaDomainEventPublisherAdapter adapter;
    private KafkaConsumer<String, String> consumer;
    private DefaultKafkaProducerFactory<String, String> producerFactory;

    @BeforeEach
    void setUp() {
        Map<String, Object> producerConfig = Map.of(
                "bootstrap.servers", BOOTSTRAP_SERVERS,
                "key.serializer", StringSerializer.class,
                "value.serializer", StringSerializer.class
        );
        producerFactory = new DefaultKafkaProducerFactory<>(producerConfig);
        KafkaTemplate<String, String> kafkaTemplate = new KafkaTemplate<>(producerFactory);

        ObjectMapper objectMapper = new ObjectMapper();
        adapter = new KafkaDomainEventPublisherAdapter(kafkaTemplate, objectMapper);

        consumer = new KafkaConsumer<>(Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, BOOTSTRAP_SERVERS,
                ConsumerConfig.GROUP_ID_CONFIG, "test-group-" + UUID.randomUUID(),
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "latest",
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class
        ));
        consumer.subscribe(Collections.singletonList(TOPIC));
        consumer.poll(Duration.ofMillis(500));
    }

    @AfterEach
    void tearDown() {
        consumer.close();
        producerFactory.destroy();
    }

    @Test
    void shouldPublishPayloadToKafkaWithCorrelationIdAsKey() {
        UUID correlationId = UUID.randomUUID();
        SagaPayload payload = SagaPayloadFixture.withIds("TX-KAFKA-001", correlationId, new CardToken("token-abc"));

        adapter.publish(payload);

        List<ConsumerRecord<String, String>> records = pollByKeys(Set.of(correlationId.toString()), Duration.ofSeconds(10));

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

        List<ConsumerRecord<String, String>> records = pollByKeys(
                Set.of(id1.toString(), id2.toString()), Duration.ofSeconds(10));

        assertThat(records).hasSize(2);
        assertThat(records.stream().map(ConsumerRecord::key))
                .containsExactlyInAnyOrder(id1.toString(), id2.toString());
    }

    private SagaPayload buildPayload(String transactionId, UUID correlationId) {
        return SagaPayloadFixture.withIds(transactionId, correlationId, new CardToken("token"));
    }

    private List<ConsumerRecord<String, String>> pollByKeys(Set<String> keys, Duration timeout) {
        List<ConsumerRecord<String, String>> result = new ArrayList<>();
        long deadline = System.currentTimeMillis() + timeout.toMillis();
        while (System.currentTimeMillis() < deadline && result.size() < keys.size()) {
            consumer.poll(Duration.ofMillis(500))
                    .records(TOPIC)
                    .forEach(r -> { if (keys.contains(r.key())) result.add(r); });
        }
        return result;
    }
}
