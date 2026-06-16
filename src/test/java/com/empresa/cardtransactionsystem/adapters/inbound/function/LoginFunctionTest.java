package com.empresa.cardtransactionsystem.adapters.inbound.function;

import com.empresa.cardtransactionsystem.config.FunctionsConfig;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.opentelemetry.api.OpenTelemetry;
import com.empresa.cardtransactionsystem.adapters.inbound.rest.dto.JwtResponse;
import com.empresa.cardtransactionsystem.adapters.inbound.rest.dto.LoginRequest;
import com.empresa.cardtransactionsystem.domain.model.auth.JwtToken;
import com.empresa.cardtransactionsystem.domain.ports.input.LoginUseCase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.function.Function;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
@ExtendWith(MockitoExtension.class)
@DisplayName("LoginFunction")
class LoginFunctionTest {
    @Mock
    private LoginUseCase loginUseCase;
    private Function<LoginRequest, JwtResponse> function;
    @BeforeEach
    void setUp() {
        FunctionsConfig config = new FunctionsConfig(OpenTelemetry.noop(), new SimpleMeterRegistry());
        function = config.loginFunction(loginUseCase);
    }
    @Test
    @DisplayName("should delegate login to use case and return JWT")
    void shouldDelegateToUseCaseAndReturnJwt() {
        var request = new LoginRequest("reinaldo", "secret");
        when(loginUseCase.login("reinaldo", "secret")).thenReturn(new JwtToken("jwt-token-value"));
        JwtResponse response = function.apply(request);
        assertThat(response.token()).isEqualTo("jwt-token-value");
        verify(loginUseCase).login("reinaldo", "secret");
    }
}