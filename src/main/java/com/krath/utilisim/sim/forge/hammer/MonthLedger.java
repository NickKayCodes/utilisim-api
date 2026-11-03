package com.krath.utilisim.sim.forge.hammer;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Aggregate row for one month of a plan timeline (CURRENT_PLAN_DESIGN §8.2).
 * The per-debt rows are carried alongside so the UI can expand a month.
 */
public record MonthLedger(
        int monthIndex,
        String monthLabel,
        LocalDate periodStart,
        BigDecimal startingBalance,
        BigDecimal monthlyInterest,
        BigDecimal minimumDue,
        BigDecimal actualPayment,
        BigDecimal extraPayment,
        BigDecimal lumpSumApplied,
        BigDecimal remainingBalance,
        BigDecimal utilizationPct,
        BigDecimal interestPaid,
        BigDecimal amountToCoverInterest,
        List<DebtMonthLedger> debts
) {
}
