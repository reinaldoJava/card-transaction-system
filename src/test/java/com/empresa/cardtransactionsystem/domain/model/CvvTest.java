package com.empresa.cardtransactionsystem.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Cvv Value Object")
class CvvTest {

    @Test
    @DisplayName("should create when value has 3 digits")
    void shouldCreateCvvWith3Digits() {
        Cvv cvv = new Cvv("123");
        assertThat(cvv.value()).isEqualTo("123");
    }

    @Test
    @DisplayName("should create when value has 4 digits (AMEX)")
    void shouldCreateCvvWith4Digits() {
        Cvv cvv = new Cvv("1234");
        assertThat(cvv.value()).isEqualTo("1234");
    }

    @Test
    @DisplayName("should reject when value has less than 3 digits")
    void shouldRejectCvvWithLessThan3Digits() {
        assertThatThrownBy(() -> new Cvv("12"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("CVV");
    }

    @Test
    @DisplayName("should reject when value has more than 4 digits")
    void shouldRejectCvvWithMoreThan4Digits() {
        assertThatThrownBy(() -> new Cvv("12345"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("should reject null value")
    void shouldRejectNullCvv() {
        assertThatThrownBy(() -> new Cvv(null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
