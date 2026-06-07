package com.empresa.cardtransactionsystem.adapters.inbound.rest;

import com.empresa.cardtransactionsystem.adapters.inbound.rest.dto.CardTransactionRequest;
import com.empresa.cardtransactionsystem.adapters.inbound.rest.dto.TransactionInitiatedResponse;
import com.empresa.cardtransactionsystem.application.orchestrator.TransactionOrchestrator;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ProcessController {

    private final TransactionOrchestrator orchestrator;

    public ProcessController(TransactionOrchestrator orchestrator) {
        this.orchestrator = orchestrator;
    }

    @PostMapping("/process")
    public ResponseEntity<TransactionInitiatedResponse> process(@RequestBody @Valid CardTransactionRequest request) {
        var correlationId = orchestrator.orchestrate(request);
        return ResponseEntity.accepted()
                .body(new TransactionInitiatedResponse(request.transactionId(), correlationId));
    }
}
