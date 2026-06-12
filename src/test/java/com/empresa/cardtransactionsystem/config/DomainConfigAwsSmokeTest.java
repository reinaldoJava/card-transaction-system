package com.empresa.cardtransactionsystem.config;

import com.empresa.cardtransactionsystem.adapters.outbound.bedrock.BedrockFraudAnalysisAdapter;
import com.empresa.cardtransactionsystem.adapters.outbound.dynamodb.DynamoDbCacheAdapter;
import com.empresa.cardtransactionsystem.adapters.outbound.dynamodb.DynamoDbClientProfileAdapter;
import com.empresa.cardtransactionsystem.adapters.outbound.dynamodb.DynamoDbTransactionAdapter;
import com.empresa.cardtransactionsystem.adapters.outbound.dynamodb.DynamoDbUserRepositoryAdapter;
import com.empresa.cardtransactionsystem.adapters.outbound.dynamodb.DynamoDbTransactionHistoryAdapter;
import com.empresa.cardtransactionsystem.adapters.outbound.lambda.LambdaTokenExchangeAdapter;
import com.empresa.cardtransactionsystem.adapters.outbound.noop.NoOpAuditAdapter;
import com.empresa.cardtransactionsystem.adapters.outbound.noop.NoOpDomainEventPublisherAdapter;
import com.empresa.cardtransactionsystem.adapters.outbound.saga.StepFunctionsSagaStarterAdapter;
import com.empresa.cardtransactionsystem.domain.ports.output.AuditSearchPort;
import com.empresa.cardtransactionsystem.domain.ports.output.CachePort;
import com.empresa.cardtransactionsystem.domain.ports.output.ClientProfilePort;
import com.empresa.cardtransactionsystem.domain.ports.output.DomainEventPublisherPort;
import com.empresa.cardtransactionsystem.domain.ports.output.FraudAnalysisPort;
import com.empresa.cardtransactionsystem.domain.ports.output.SagaStarterPort;
import com.empresa.cardtransactionsystem.domain.ports.output.TransactionHistoryPort;
import com.empresa.cardtransactionsystem.domain.ports.output.TransactionRepositoryPort;
import com.empresa.cardtransactionsystem.domain.ports.output.UserRepositoryPort;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.springframework.test.context.bean.override.convention.TestBean;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.services.lambda.LambdaClient;
import software.amazon.awssdk.services.sfn.SfnClient;
import software.amazon.awssdk.services.ssm.SsmClient;
import software.amazon.awssdk.services.ssm.model.GetParameterRequest;
import software.amazon.awssdk.services.ssm.model.GetParameterResponse;
import software.amazon.awssdk.services.ssm.model.Parameter;

import org.springframework.test.annotation.DirtiesContext;
import static org.assertj.core.api.Assertions.assertThat;

@DirtiesContext
@SpringBootTest(properties = {
        "spring.autoconfigure.exclude=" +
                "org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration," +
                "org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration," +
                "org.springframework.boot.jdbc.autoconfigure.DataSourceInitializationAutoConfiguration," +
                "org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration," +
                "org.springframework.boot.data.jpa.autoconfigure.JpaRepositoriesAutoConfiguration," +
                "org.springframework.boot.actuate.autoconfigure.metrics.export.otlp.OtlpMetricsExportAutoConfiguration",
        "auth.lambda.function-name=token-exchange-lambda",
        "auth.hardcoded-opaque-token=test-opaque-token",
        "aws.region=sa-east-1",
        "step-functions.state-machine-arn=arn:aws:states:sa-east-1:000000000000:stateMachine:test",
        "spring.cloud.function.definition="
})
@ActiveProfiles({"aws", "cache-dynamodb", "queue-none", "saga-stepfunctions", "ledger-dynamodb", "fraud-bedrock", "env-aws","test"})
//TODO Analisar porque esse teste não está funcionando.
class DomainConfigAwsSmokeTest {

    @TestConfiguration
    static class TestInfraConfig {
        @Bean
        @Primary
        SsmClient ssmClient() {
            SsmClient mock = Mockito.mock(SsmClient.class);
            Parameter param = Parameter.builder()
                    .value("test-jwt-secret-key-32-bytes-min!!")
                    .build();
            GetParameterResponse resp = GetParameterResponse.builder()
                    .parameter(param)
                    .build();
            Mockito.when(mock.getParameter(Mockito.any(GetParameterRequest.class))).thenReturn(resp);
            return mock;
        }
    }

    @Autowired
    private ApplicationContext context;

    @MockitoBean private DynamoDbEnhancedClient enhancedClient;
    @MockitoBean private SfnClient sfnClient;
    @MockitoBean private BedrockRuntimeClient bedrockClient;
    // Substitui o FlushableOtlpMeterRegistry (env-aws) por um registry real e sem rede,
    // mantendo o @Primary — para o TransactionMetrics conseguir registrar os meters no contexto de teste.
    @TestBean(name = "otlpMeterRegistry")
    private MeterRegistry otlpMeterRegistry;

    static MeterRegistry otlpMeterRegistry() {
        return new SimpleMeterRegistry();
    }

    @MockitoBean private LambdaClient lambdaClient;
    @MockitoBean private LambdaTokenExchangeAdapter lambdaTokenExchangeAdapter;

    @Test
    void shouldBindCachePortToDynamoDbAdapter() {
        assertThat(context.getBean(CachePort.class))
                .isInstanceOf(DynamoDbCacheAdapter.class);
    }

    @Test
    void shouldBindTransactionRepositoryPortToDynamoDbAdapter() {
        assertThat(context.getBean(TransactionRepositoryPort.class))
                .isInstanceOf(DynamoDbTransactionAdapter.class);
    }

    @Test
    void shouldBindUserRepositoryPortToDynamoDbAdapter() {
        assertThat(context.getBean(UserRepositoryPort.class))
                .isInstanceOf(DynamoDbUserRepositoryAdapter.class);
    }

    @Test
    void shouldBindClientProfilePortToDynamoDbAdapter() {
        assertThat(context.getBean(ClientProfilePort.class))
                .isInstanceOf(DynamoDbClientProfileAdapter.class);
    }

    @Test
    void shouldBindSagaStarterPortToStepFunctionsAdapter() {
        assertThat(context.getBean(SagaStarterPort.class))
                .isInstanceOf(StepFunctionsSagaStarterAdapter.class);
    }

    @Test
    void shouldBindFraudAnalysisPortToBedrockAdapter() {
        assertThat(context.getBean(FraudAnalysisPort.class))
                .isInstanceOf(BedrockFraudAnalysisAdapter.class);
    }

    @Test
    void shouldBindDomainEventPublisherPortToNoOpAdapter() {
        assertThat(context.getBean(DomainEventPublisherPort.class))
                .isInstanceOf(NoOpDomainEventPublisherAdapter.class);
    }

    @Test
    void shouldBindAuditSearchPortToNoOpAdapter() {
        assertThat(context.getBean(AuditSearchPort.class))
                .isInstanceOf(NoOpAuditAdapter.class);
    }

    @Test
    void shouldBindTransactionHistoryPortToDynamoDbAdapter() {
        assertThat(context.getBean(TransactionHistoryPort.class))
                .isInstanceOf(DynamoDbTransactionHistoryAdapter.class);
    }
}
