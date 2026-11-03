package com.krath.utilisim.ledger.leaf.create;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record TomeResponse(
        String id,
        String userId,
        String name,
        BigDecimal currentBalance,
        BigDecimal apr,
        BigDecimal estimatedApr,
        BigDecimal minimumDebtPayment,
        BigDecimal currentPayment,
        BigDecimal creditLimit,
        String type,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {}
