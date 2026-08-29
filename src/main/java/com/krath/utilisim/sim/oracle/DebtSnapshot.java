package com.krath.utilisim.sim.oracle;

/**
 * DTO representing a debt/account snapshot for strategy ordering.
 * Used during strategy calculation to determine payment priorities.
 */
public record DebtSnapshot(
    String tomeId,
    String accountName,
    float currentBalance,
    float apr,
    float estimatedApr,
    float minimumPayment,
    float interestRate
) {}

