package com.empresa.cardtransactionsystem.domain.service;

import com.empresa.cardtransactionsystem.domain.model.Brand;
import com.empresa.cardtransactionsystem.domain.model.CardData;
import com.empresa.cardtransactionsystem.domain.model.CardNumber;
import com.empresa.cardtransactionsystem.domain.model.CardToken;
import com.empresa.cardtransactionsystem.domain.model.Cvv;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("CardValidationService")
class CardValidationServiceTest {

    private CardValidationService service;

    @BeforeEach
    void setUp() {
        service = new CardValidationService();
    }

    @Test
    @DisplayName("should generate deterministic card token for same card number")
    void shouldGenerateDeterministicToken() {
        CardData cardData = cardData("4111111111111111", "123");
        CardToken first = service.tokenize(cardData);
        CardToken second = service.tokenize(cardData);
        assertThat(first.value()).isEqualTo(second.value());
    }

    @Test
    @DisplayName("should generate different tokens for different card numbers")
    void shouldGenerateDifferentTokensForDifferentNumbers() {
        CardToken visa = service.tokenize(cardData("4111111111111111", "123"));
        CardToken master = service.tokenize(cardData("5500005555555559", "456"));
        assertThat(visa.value()).isNotEqualTo(master.value());
    }

    @Test
    @DisplayName("card token should be UUID format")
    void cardTokenShouldBeUuidFormat() {
        CardToken token = service.tokenize(cardData("4111111111111111", "123"));
        assertThat(token.value())
                .matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
    }

    private CardData cardData(String number, String cvv) {
        return new CardData(new CardNumber(number), new Cvv(cvv), "John Doe", Brand.VISA);
    }
}
