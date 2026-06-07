package com.empresa.cardtransactionsystem.adapters.outbound;

import com.empresa.cardtransactionsystem.adapters.outbound.bedrock.BedrockFraudAnalysisAdapter;
import com.empresa.cardtransactionsystem.adapters.outbound.ollama.OllamaFraudAnalysisAdapter;
import com.empresa.cardtransactionsystem.domain.ports.output.FraudAnalysisPort;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;

@Configuration
public class FraudAnalysisAdapterFactory {

    private final BedrockFraudAnalysisAdapter bedrockAdapter;
    private final OllamaFraudAnalysisAdapter ollamaAdapter;
    private final FraudAnalysisProperties properties;

    public FraudAnalysisAdapterFactory(
            BedrockFraudAnalysisAdapter bedrockAdapter,
            OllamaFraudAnalysisAdapter ollamaAdapter,
            FraudAnalysisProperties properties) {
        this.bedrockAdapter = bedrockAdapter;
        this.ollamaAdapter = ollamaAdapter;
        this.properties = properties;
    }

    @Bean
    public FraudAnalysisPort fraudAnalysisPort() {
        return properties.provider == FraudAnalysisProvider.OLLAMA
            ? ollamaAdapter
            : bedrockAdapter;
    }

    @Component
    @ConfigurationProperties(prefix = "fraud-analysis")
    public static class FraudAnalysisProperties {
        public FraudAnalysisProvider provider = FraudAnalysisProvider.BEDROCK;

        public FraudAnalysisProvider getProvider() {
            return provider;
        }

        public void setProvider(FraudAnalysisProvider provider) {
            this.provider = provider;
        }
    }
}
