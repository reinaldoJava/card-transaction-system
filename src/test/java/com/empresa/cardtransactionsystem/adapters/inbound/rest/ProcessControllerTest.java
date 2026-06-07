package com.empresa.cardtransactionsystem.adapters.inbound.rest;

import com.empresa.cardtransactionsystem.application.orchestrator.TransactionOrchestrator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ProcessController.class)
@DisplayName("ProcessController")
class ProcessControllerTest {

    @Autowired MockMvc mockMvc;
    @MockitoBean TransactionOrchestrator orchestrator;

    @Test
    @DisplayName("should return 202 Accepted with correlationId when request is valid")
    void shouldReturn202WhenRequestIsValid() throws Exception {
        var correlationId = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");
        when(orchestrator.orchestrate(any())).thenReturn(correlationId);

        mockMvc.perform(post("/process")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequestJson()))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.transactionId").value("TXN-001"))
                .andExpect(jsonPath("$.correlationId").value(correlationId.toString()));

        verify(orchestrator).orchestrate(any());
    }

    @Test
    @DisplayName("should return 400 when transactionId is blank")
    void shouldReturn400WhenTransactionIdIsBlank() throws Exception {
        mockMvc.perform(post("/process")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "transactionId": "",
                                  "uuidTransaction": "550e8400-e29b-41d4-a716-446655440000",
                                  "cardDataRequest": { "number": "4111111111111111", "cvv": "123", "name": "John Doe", "brand": "VISA" },
                                  "amount": 500.00,
                                  "installments": 1
                                }
                                """))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(orchestrator);
    }

    @Test
    @DisplayName("should return 400 when card number is invalid")
    void shouldReturn400WhenCardNumberIsInvalid() throws Exception {
        mockMvc.perform(post("/process")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "transactionId": "TXN-001",
                                  "uuidTransaction": "550e8400-e29b-41d4-a716-446655440000",
                                  "cardDataRequest": { "number": "123", "cvv": "123", "name": "John Doe", "brand": "VISA" },
                                  "amount": 500.00,
                                  "installments": 1
                                }
                                """))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(orchestrator);
    }

    @Test
    @DisplayName("should return 400 when CVV is invalid")
    void shouldReturn400WhenCvvIsInvalid() throws Exception {
        mockMvc.perform(post("/process")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "transactionId": "TXN-001",
                                  "uuidTransaction": "550e8400-e29b-41d4-a716-446655440000",
                                  "cardDataRequest": { "number": "4111111111111111", "cvv": "12", "name": "John Doe", "brand": "VISA" },
                                  "amount": 500.00,
                                  "installments": 1
                                }
                                """))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(orchestrator);
    }

    @Test
    @DisplayName("should return 400 when installments exceeds maximum")
    void shouldReturn400WhenInstallmentsExceedsMax() throws Exception {
        mockMvc.perform(post("/process")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "transactionId": "TXN-001",
                                  "uuidTransaction": "550e8400-e29b-41d4-a716-446655440000",
                                  "cardDataRequest": { "number": "4111111111111111", "cvv": "123", "name": "John Doe", "brand": "VISA" },
                                  "amount": 500.00,
                                  "installments": 25
                                }
                                """))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(orchestrator);
    }

    @Test
    @DisplayName("should return 400 when amount is zero")
    void shouldReturn400WhenAmountIsZero() throws Exception {
        mockMvc.perform(post("/process")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "transactionId": "TXN-001",
                                  "uuidTransaction": "550e8400-e29b-41d4-a716-446655440000",
                                  "cardDataRequest": { "number": "4111111111111111", "cvv": "123", "name": "John Doe", "brand": "VISA" },
                                  "amount": 0,
                                  "installments": 1
                                }
                                """))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(orchestrator);
    }

    private String validRequestJson() {
        return """
                {
                  "transactionId": "TXN-001",
                  "uuidTransaction": "550e8400-e29b-41d4-a716-446655440000",
                  "cardDataRequest": {
                    "number": "4111111111111111",
                    "cvv": "123",
                    "name": "John Doe",
                    "brand": "VISA"
                  },
                  "amount": 500.00,
                  "installments": 3
                }
                """;
    }
}
