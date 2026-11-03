package com.krath.utilisim.sim.oracle;

/**
 * DTO representing a debt/account snapshot for strategy ordering.
 * Used during strategy calculation to determine payment priorities.
 *
 * <p><b>Units:</b> {@code apr} and {@code estimatedApr} are annual
 * <b>percentages</b> ({@code 19.99} = 19.99%). Convert them with
 * {@code AprRates} before doing any balance math. {@code interestRate} is
 * carried through for clients and is also a percentage; it is not used by the
 * Oracle calculations.</p>
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
