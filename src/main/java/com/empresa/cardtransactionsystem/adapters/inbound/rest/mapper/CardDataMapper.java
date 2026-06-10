package com.empresa.cardtransactionsystem.adapters.inbound.rest.mapper;

import com.empresa.cardtransactionsystem.adapters.inbound.rest.dto.CardDataRequest;
import com.empresa.cardtransactionsystem.domain.model.Brand;
import com.empresa.cardtransactionsystem.domain.model.CardData;
import com.empresa.cardtransactionsystem.domain.model.CardNumber;
import com.empresa.cardtransactionsystem.domain.model.Cvv;
import org.springframework.stereotype.Component;

@Component
public class CardDataMapper {
    public CardData toDomain(CardDataRequest request){
        return new CardData(
                new CardNumber(request.number()),
                new Cvv(request.cvv()),
                request.name(),
                Brand.valueOf(request.brand())
        );
    }
}
