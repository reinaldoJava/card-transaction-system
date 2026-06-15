package com.empresa.cardtransactionsystem.adapters.inbound.rest;

import com.empresa.cardtransactionsystem.domain.model.auth.JwtToken;
import com.empresa.cardtransactionsystem.domain.ports.input.LoginUseCase;
import com.empresa.cardtransactionsystem.domain.exception.UnauthorizedException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AuthController.class)
@AutoConfigureMockMvc(addFilters = false)
@DisplayName("AuthController")
class AuthControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean LoginUseCase loginUseCase;

    @Test
    @DisplayName("should return 200 with JWT when credentials are valid")
    void shouldReturn200WithJwtOnValidCredentials() throws Exception {
        when(loginUseCase.login("john", "secret")).thenReturn(new JwtToken("eyJ.payload.sig"));

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"john","password":"secret"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("eyJ.payload.sig"));
    }

    @Test
    @DisplayName("should return 401 when credentials are invalid")
    void shouldReturn401OnInvalidCredentials() throws Exception {
        when(loginUseCase.login(anyString(), anyString()))
                .thenThrow(new UnauthorizedException("Invalid credentials"));

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"john","password":"wrongpass"}
                                """))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("should return 400 when username is blank")
    void shouldReturn400WhenUsernameIsBlank() throws Exception {
        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"","password":"secret"}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("should return 400 when password is blank")
    void shouldReturn400WhenPasswordIsBlank() throws Exception {
        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"john","password":""}
                                """))
                .andExpect(status().isBadRequest());
    }
}
