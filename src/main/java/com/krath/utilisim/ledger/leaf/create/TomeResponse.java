package com.krath.utilisim.ledger.leaf.create;

import java.time.OffsetDateTime;

public record TomeResponse(
        String id,
        String userId,
        String name,
        float currentBalance,
        Float apr,
        float estimatedApr,
        float minimumDebtPayment,
        float creditLimit,
        String type,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {}