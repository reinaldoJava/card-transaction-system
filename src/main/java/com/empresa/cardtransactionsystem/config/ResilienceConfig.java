package com.empresa.cardtransactionsystem.config;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * Circuit breakers (Resilience4j core, uso programatico) para os adapters de IA.
 * Quando o provedor (Ollama local / Bedrock prod) falha repetidamente, o breaker abre e
 * passa a falhar rapido (sem chamar o provedor), deixando a saga cair nas regras de degrade.
 * Periodicamente (HALF_OPEN) ele sonda se o provedor voltou.
 */
@Configuration
public class ResilienceConfig {

    private static final Logger log = LoggerFactory.getLogger(ResilienceConfig.class);

    @Bean
    public CircuitBreakerRegistry circuitBreakerRegistry() {
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
                .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
                .slidingWindowSize(10)
                .minimumNumberOfCalls(5)
                .failureRateThreshold(50f)
                .waitDurationInOpenState(Duration.ofSeconds(15))
                .permittedNumberOfCallsInHalfOpenState(2)
                .automaticTransitionFromOpenToHalfOpenEnabled(true)
                .build();
        return CircuitBreakerRegistry.of(config);
    }

    @Bean(name = "ollamaFraudCircuitBreaker")
    public CircuitBreaker ollamaFraudCircuitBreaker(CircuitBreakerRegistry registry) {
        return withLogging(registry.circuitBreaker("ollamaFraud"), "Ollama");
    }

    @Bean(name = "bedrockFraudCircuitBreaker")
    public CircuitBreaker bedrockFraudCircuitBreaker(CircuitBreakerRegistry registry) {
        return withLogging(registry.circuitBreaker("bedrockFraud"), "Bedrock");
    }

    private CircuitBreaker withLogging(CircuitBreaker cb, String provider) {
        cb.getEventPublisher().onStateTransition(event -> {
            CircuitBreaker.StateTransition t = event.getStateTransition();
            if (t.getToState() == CircuitBreaker.State.OPEN) {
                log.warn("Circuit breaker {} OPEN — {} indisponivel; degradando para regras estaticas.",
                        cb.getName(), provider);
            } else if (t.getToState() == CircuitBreaker.State.HALF_OPEN) {
                log.warn("Circuit breaker {} HALF_OPEN — sondando se o {} voltou.", cb.getName(), provider);
            } else if (t.getToState() == CircuitBreaker.State.CLOSED) {
                log.info("Circuit breaker {} CLOSED — {} recuperado.", cb.getName(), provider);
            }
        });
        return cb;
    }
}
