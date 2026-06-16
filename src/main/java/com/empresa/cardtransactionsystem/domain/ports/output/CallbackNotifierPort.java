package com.empresa.cardtransactionsystem.domain.ports.output;

import com.empresa.cardtransactionsystem.domain.model.SagaPayload;
import com.empresa.cardtransactionsystem.domain.model.TransactionResult;

public interface CallbackNotifierPort {
    void notify(SagaPayload payload, TransactionResult result);
}
