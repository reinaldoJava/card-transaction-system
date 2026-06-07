package com.empresa.cardtransactionsystem.config;

import com.empresa.cardtransactionsystem.domain.service.CardValidationService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class DomainConfig {

    @Bean
    public CardValidationService cardValidationService() {
        return new CardValidationService();
    }
}
