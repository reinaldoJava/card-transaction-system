package com.empresa.cardtransactionsystem.adapters.outbound.postgres;

import com.empresa.cardtransactionsystem.domain.model.Brand;
import com.empresa.cardtransactionsystem.domain.model.CardToken;
import com.empresa.cardtransactionsystem.domain.model.SagaPayload;
import com.empresa.cardtransactionsystem.domain.model.TransactionStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("integration")
@DataJpaTest(properties = {
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.datasource.driver-class-name=org.postgresql.Driver",
        "spring.test.database.replace=none",
        "spring.datasource.url=jdbc:postgresql://localhost:5432/cards",
        "spring.datasource.username=postgres",
        "spring.datasource.password=postgres"
})
class PostgresTransactionAdapterTest {

    @Autowired
    private TransactionJpaRepository repository;

    private PostgresTransactionAdapter adapter;

    @BeforeEach
    void setUp() {
        repository.deleteAll();
        adapter = new PostgresTransactionAdapter(repository);
    }

    @Test
    void shouldSaveAndFindById() {
        SagaPayload payload = buildPayload(UUID.randomUUID(), TransactionStatus.PENDING);

        adapter.save(payload);
        Optional<SagaPayload> found = adapter.findById(payload.correlationId());

        assertThat(found).isPresent();
        assertThat(found.get().transactionId()).isEqualTo(payload.transactionId());
        assertThat(found.get().status()).isEqualTo(TransactionStatus.PENDING);
    }

    @Test
    void shouldUpdateStatus() {
        UUID correlationId = UUID.randomUUID();
        adapter.save(buildPayload(correlationId, TransactionStatus.PENDING));

        adapter.updateStatus(correlationId, TransactionStatus.APPROVED);

        assertThat(adapter.findStatus(correlationId))
                .contains(TransactionStatus.APPROVED);
    }

    @Test
    void shouldReturnEmptyForUnknownId() {
        assertThat(adapter.findById(UUID.randomUUID())).isEmpty();
        assertThat(adapter.findStatus(UUID.randomUUID())).isEmpty();
    }

    @Test
    void shouldOverwriteOnSave() {
        UUID correlationId = UUID.randomUUID();
        adapter.save(buildPayload(correlationId, TransactionStatus.PENDING));
        adapter.save(buildPayload(correlationId, TransactionStatus.APPROVED));

        assertThat(adapter.findStatus(correlationId)).contains(TransactionStatus.APPROVED);
    }

    private SagaPayload buildPayload(UUID correlationId, TransactionStatus status) {
        return new SagaPayload(
                "TX-PG-" + correlationId.toString().substring(0, 8),
                correlationId,
                new CardToken("tok-" + correlationId),
                new BigDecimal("500.00"),
                3,
                Brand.VISA,
                status,
                LocalDateTime.now(),
                null, null);
    }
}
