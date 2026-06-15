package com.empresa.cardtransactionsystem.adapters.inbound.rest.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorResponse(
        Instant timestamp,
        int status,
        String error,
        String message,
        String path,
        List<FieldError> fields
) {
    public ErrorResponse(int status, String error, String message) {
        this(Instant.now(), status, error, message, null, null);
    }

    public ErrorResponse(int status, String error, String message, String path) {
        this(Instant.now(), status, error, message, path, null);
    }

    public ErrorResponse(int status, String error, String message, List<FieldError> fields) {
        this(Instant.now(), status, error, message, null, fields);
    }
}
