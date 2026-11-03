package com.krath.utilisim.ledger.leaf.create;

import java.math.BigDecimal;

public record TomeRequest(
        String name,
        BigDecimal currentBalance,
        BigDecimal apr,               // nullable; the estimate is used when absent
        BigDecimal minimumDebtPayment,
        BigDecimal currentPayment,     // nullable; defaults to the minimum
        BigDecimal creditLimit,
        String type
) {}
