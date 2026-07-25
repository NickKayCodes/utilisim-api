package com.krath.utilisim.ledger.vault;

import com.krath.utilisim.ledger.leaf.create.TomeResponse;
import com.krath.utilisim.ledger.tome.Tome;

import java.util.List;

public class TomeMapper {
    public static TomeResponse toResponse(Tome tome) {
        return new TomeResponse(
                tome.getId(),
                tome.getUserId(),
                tome.getName(),
                tome.getCurrentBalance(),
                tome.getApr(),
                tome.getEstimatedApr(),
                tome.getMinimumDebtPayment(),
                tome.getCreditLimit(),
                tome.getType(),
                tome.getCreatedAt(),
                tome.getUpdatedAt()
        );
    }

    public static List<TomeResponse> toResponseList(List<Tome> tomes) {
        return tomes.stream().map(TomeMapper::toResponse).toList();
    }
}

