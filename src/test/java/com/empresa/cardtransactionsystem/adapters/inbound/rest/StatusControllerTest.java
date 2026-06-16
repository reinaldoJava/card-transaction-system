package com.empresa.cardtransactionsystem.adapters.inbound.rest;

import com.empresa.cardtransactionsystem.domain.model.TransactionResult;
import com.empresa.cardtransactionsystem.domain.model.TransactionStatus;
import com.empresa.cardtransactionsystem.domain.ports.input.GetTransactionStatusUseCase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;
import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(StatusController.class)
@AutoConfigureMockMvc(addFilters = false)
@DisplayName("StatusController")
class StatusControllerTest {

    @Autowired MockMvc mockMvc;
    @MockitoBean GetTransactionStatusUseCase getTransactionStatusUseCase;

    @Test
    @DisplayName("should return 200 with APPROVED status when transaction exists")
    void shouldReturn200WithStatusWhenFound() throws Exception {
        var correlationId = UUID.randomUUID();
        when(getTransactionStatusUseCase.getStatus(correlationId))
                .thenReturn(Optional.of(TransactionResult.approved(correlationId)));

        mockMvc.perform(get("/status/{correlationId}", correlationId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.correlationId").value(correlationId.toString()))
                .andExpect(jsonPath("$.status").value("APPROVED"));
    }

    @Test
    @DisplayName("should return 200 with PENDING status when saga is in progress")
    void shouldReturn200WithPendingStatus() throws Exception {
        var correlationId = UUID.randomUUID();
        when(getTransactionStatusUseCase.getStatus(correlationId))
                .thenReturn(Optional.of(new TransactionResult(correlationId, TransactionStatus.PENDING, null)));

        mockMvc.perform(get("/status/{correlationId}", correlationId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    @DisplayName("should return 404 when correlationId is not found")
    void shouldReturn404WhenNotFound() throws Exception {
        var correlationId = UUID.randomUUID();
        when(getTransactionStatusUseCase.getStatus(correlationId)).thenReturn(Optional.empty());

        mockMvc.perform(get("/status/{correlationId}", correlationId))
                .andExpect(status().isNotFound());
    }
}
