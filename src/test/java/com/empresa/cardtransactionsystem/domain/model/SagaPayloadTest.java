package com.empresa.cardtransactionsystem.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("SagaPayload")
class SagaPayloadTest {

    private static final CardToken TOKEN = new CardToken("tok-123");
    private static final UUID CORRELATION = UUID.randomUUID();

    @Test
    @DisplayName("pending() creates payload with PENDING status and null traceparent")
    void pendingCreatesNullTraceparent() {
        SagaPayload payload = SagaPayload.pending("TXN-1", CORRELATION, TOKEN,
                BigDecimal.TEN, 1, Brand.VISA,"URL");

        assertThat(payload.status()).isEqualTo(TransactionStatus.PENDING);
        assertThat(payload.traceparent()).isNull();
    }

    @Test
    @DisplayName("rejected() creates payload with REJECTED status and null traceparent")
    void rejectedCreatesNullTraceparent() {
        SagaPayload payload = SagaPayload.rejected("TXN-1", CORRELATION, TOKEN,
                BigDecimal.TEN, 1, Brand.VISA,"URL");

        assertThat(payload.status()).isEqualTo(TransactionStatus.REJECTED);
        assertThat(payload.traceparent()).isNull();
    }

    @Test
    @DisplayName("withTraceparent() returns new record with traceparent set, preserving all other fields")
    void withTraceparentPreservesAllFields() {
        SagaPayload original = SagaPayload.pending("TXN-1", CORRELATION, TOKEN,
                BigDecimal.TEN, 1, Brand.VISA,"URL");
        String tp = "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01";

        SagaPayload updated = original.withTraceparent(tp);

        assertThat(updated.traceparent()).isEqualTo(tp);
        assertThat(updated.transactionId()).isEqualTo(original.transactionId());
        assertThat(updated.correlationId()).isEqualTo(original.correlationId());
        assertThat(updated.status()).isEqualTo(original.status());
        assertThat(updated.amount()).isEqualTo(original.amount());
    }

    @Test
    @DisplayName("withTraceparent() is immutable — original is unchanged")
    void withTraceparentIsImmutable() {
        SagaPayload original = SagaPayload.pending("TXN-1", CORRELATION, TOKEN,
                BigDecimal.TEN, 1, Brand.VISA,"URL");

        original.withTraceparent("00-abc-01");

        assertThat(original.traceparent()).isNull();
    }
}
