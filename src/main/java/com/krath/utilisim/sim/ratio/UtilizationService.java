package com.krath.utilisim.sim.ratio;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * RATIO's only responsibility under CURRENT_PLAN_DESIGN §3.2: convert balances
 * and limits into utilization percentages. It performs no amortization.
 */
@Service
public class UtilizationService {

    private static final BigDecimal ONE_HUNDRED = BigDecimal.valueOf(100);

    /** {@code remaining / creditLimit * 100}, or {@code null} when no limit is known. */
    public BigDecimal percent(BigDecimal remaining, BigDecimal creditLimit) {
        if (remaining == null || creditLimit == null
                || creditLimit.compareTo(BigDecimal.ZERO) <= 0) {
            return null;
        }
        return remaining.multiply(ONE_HUNDRED)
                .divide(creditLimit, 2, RoundingMode.HALF_UP);
    }

    /**
     * Aggregate utilization over the debts that have a positive limit.
     * Returns {@code null} when no debt has a usable limit.
     */
    public BigDecimal aggregatePercent(List<BigDecimal> remaining, List<BigDecimal> limits) {
        BigDecimal totalRemaining = BigDecimal.ZERO;
        BigDecimal totalLimit = BigDecimal.ZERO;
        for (int i = 0; i < remaining.size(); i++) {
            BigDecimal limit = limits.get(i);
            if (limit == null || limit.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            totalLimit = totalLimit.add(limit);
            totalRemaining = totalRemaining.add(remaining.get(i) == null
                    ? BigDecimal.ZERO
                    : remaining.get(i));
        }
        if (totalLimit.compareTo(BigDecimal.ZERO) <= 0) {
            return null;
        }
        return totalRemaining.multiply(ONE_HUNDRED)
                .divide(totalLimit, 2, RoundingMode.HALF_UP);
    }
}
