package com.empresa.cardtransactionsystem.domain.ports.input;

import java.util.UUID;

public interface CompensationUseCase {
    void compensate(UUID uuidTransaction);
}
