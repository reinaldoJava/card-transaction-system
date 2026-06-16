package com.empresa.cardtransactionsystem.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("CardNumber Value Object")
class CardNumberTest {

    @Test
    @DisplayName("should create when value has exactly 16 digits")
    void shouldCreateCardNumberWithValid16Digits() {
        CardNumber cardNumber = new CardNumber("4111111111111111");
        assertThat(cardNumber.value()).isEqualTo("4111111111111111");
    }

    @Test
    @DisplayName("should reject when value has less than 16 digits")
    void shouldRejectCardNumberWithLessThan16Digits() {
        assertThatThrownBy(() -> new CardNumber("411111111111111"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("card number");
    }

    @Test
    @DisplayName("should reject when value has more than 16 digits")
    void shouldRejectCardNumberWithMoreThan16Digits() {
        assertThatThrownBy(() -> new CardNumber("41111111111111111"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("should reject when value contains non-digit characters")
    void shouldRejectCardNumberWithNonDigitChars() {
        assertThatThrownBy(() -> new CardNumber("411111111111111A"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("should reject null value")
    void shouldRejectNullCardNumber() {
        assertThatThrownBy(() -> new CardNumber(null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
