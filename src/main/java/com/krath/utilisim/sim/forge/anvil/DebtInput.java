package com.krath.utilisim.sim.forge.anvil;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Immutable debt snapshot used by the FORGE ledger.
 *
 * <p>All monetary values are {@link BigDecimal}; {@code apr} is an annual
 * percentage (18.0000 = 18%). The resolved {@code currentPayment} is never
 * null: the loader defaults it to the minimum.</p>
 */
public record DebtInput(
        String tomeId,
        String accountName,
        BigDecimal currentBalance,
        BigDecimal apr,
        BigDecimal minimumPayment,
        BigDecimal currentPayment,
        BigDecimal creditLimit
) {

    /** Monthly rate as a decimal fraction: 18% -> 0.015. */
    public BigDecimal monthlyRate() {
        return apr.divide(BigDecimal.valueOf(1200), 12, RoundingMode.HALF_UP);
    }
}
