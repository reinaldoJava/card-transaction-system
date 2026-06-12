package com.empresa.cardtransactionsystem.adapters.outbound.postgres;

import com.empresa.cardtransactionsystem.domain.model.CardToken;
import com.empresa.cardtransactionsystem.domain.model.SagaPayload;
import com.empresa.cardtransactionsystem.domain.model.TransactionStatus;
import com.empresa.cardtransactionsystem.fixture.SagaPayloadFixture;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

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

    @Autowired
    private EntityManager em;

    private PostgresTransactionAdapter adapter;

    @BeforeEach
    void setUp() {
        repository.deleteAll();
        em.flush();
        em.clear();
        adapter = new PostgresTransactionAdapter(repository);
    }

    @Test
    void shouldSaveAndFindById() {
        SagaPayload payload = buildPayload(UUID.randomUUID(), TransactionStatus.PENDING);

        adapter.save(payload);
        var found = adapter.findById(payload.correlationId());

        assertThat(found).isPresent();
        assertThat(found.get().transactionId()).isEqualTo(payload.transactionId());
        assertThat(found.get().status()).isEqualTo(TransactionStatus.PENDING);
    }

    @Test
    void shouldUpdateStatus() {
        UUID correlationId = UUID.randomUUID();
        adapter.save(buildPayload(correlationId, TransactionStatus.PENDING));
        em.flush();
        em.clear();

        adapter.updateStatus(correlationId, TransactionStatus.APPROVED);

        assertThat(adapter.findStatus(correlationId)).contains(TransactionStatus.APPROVED);
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
        em.flush();
        em.clear();

        adapter.save(buildPayload(correlationId, TransactionStatus.APPROVED));
        em.flush();
        em.clear();

        assertThat(adapter.findStatus(correlationId)).contains(TransactionStatus.APPROVED);
    }

    private SagaPayload buildPayload(UUID correlationId, TransactionStatus status) {
        return SagaPayloadFixture.withIdsAndStatus(
                "TX-PG-" + correlationId.toString().substring(0, 8),
                correlationId,
                new CardToken("token-pg"),
                status);
    }
}
