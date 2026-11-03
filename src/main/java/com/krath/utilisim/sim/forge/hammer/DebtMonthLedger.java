package com.krath.utilisim.sim.forge.hammer;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * One debt's row inside a month of a plan timeline (CURRENT_PLAN_DESIGN §8.2).
 */
public record DebtMonthLedger(
        String tomeId,
        String accountName,
        BigDecimal startingBalance,
        BigDecimal monthlyInterest,
        BigDecimal minimumDue,
        BigDecimal actualPayment,
        BigDecimal extraPayment,
        BigDecimal lumpSumApplied,
        BigDecimal remainingBalance,
        BigDecimal utilizationPct,
        BigDecimal interestPaid,
        BigDecimal amountToCoverInterest
) {
}
