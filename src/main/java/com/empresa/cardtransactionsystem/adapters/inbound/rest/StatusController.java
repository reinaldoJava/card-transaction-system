package com.empresa.cardtransactionsystem.adapters.inbound.rest;

import com.empresa.cardtransactionsystem.adapters.inbound.rest.dto.TransactionStatusResponse;
import com.empresa.cardtransactionsystem.domain.ports.input.GetTransactionStatusUseCase;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
public class StatusController {

    private final GetTransactionStatusUseCase getTransactionStatusUseCase;

    public StatusController(GetTransactionStatusUseCase getTransactionStatusUseCase) {
        this.getTransactionStatusUseCase = getTransactionStatusUseCase;
    }

    @GetMapping("/status/{correlationId}")
    public ResponseEntity<TransactionStatusResponse> getStatus(@PathVariable UUID correlationId) {
        return getTransactionStatusUseCase.getStatus(correlationId)
                .map(result -> ResponseEntity.ok(
                        new TransactionStatusResponse(correlationId, result.status(), result.reason())))
                .orElse