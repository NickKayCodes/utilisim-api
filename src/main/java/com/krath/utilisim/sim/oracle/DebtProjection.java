package com.krath.utilisim.sim.oracle;

import com.krath.utilisim.sim.ratio.AprRates;

import java.util.List;

/**
 * Projects the cost of a debt payoff plan.
 *
 * <p>The previous "projection" was {@code sum(balance * apr)}: it ignored
 * payments, order and the term, so every strategy returned the same number and
 * the units were 100× too large (DEFECTS API-05/API-17). This class models the
 * behaviour strategies actually change:</p>
 *
 * <ul>
 *   <li>Interest accrues monthly on every open debt, as in
 *       {@code RatioService}.</li>
 *   <li>Every debt receives its minimum payment each month.</li>
 *   <li>The remainder of the budget (freed minimums + extra) is thrown at the
 *       first open debt in the supplied order, rolling to the next one when it
 *       closes. That is exactly what snowball/avalanche/hybrid reorder.</li>
 *   <li>An optional lump sum is applied to the open debts in the same order
 *       before month one.</li>
 * </ul>
 *
 * <p>It is a planning projection, not a lender-grade amortization: no fees, no
 * daily compounding, no variable rates. It is deterministic and it is only used
 * to rank strategies against each other.</p>
 */
public final class DebtProjection {

    /** Safety bound: 50 years of payments. */
    public static final int MAX_MONTHS = 600;

    /** Balances below this are treated as zero (sub-cent rounding noise). */
    private static final float EPSILON = 0.005f;

    private DebtProjection() {
    }

    /**
     * Outcome of paying the debts in the given order.
     *
     * @param totalInterest    interest accrued until payoff (or until the bound)
     * @param months           months simulated
     * @param repaid           true when every balance reached zero
     * @param remainingBalance unpaid balance when {@code repaid} is false
     */
    public record PayoffProjection(
            float totalInterest,
            int months,
            boolean repaid,
            float remainingBalance
    ) {
    }

    public static PayoffProjection project(
            List<DebtSnapshot> orderedDebts,
            float extraPayment,
            float lumpSumPayment
    ) {
        if (orderedDebts == null || orderedDebts.isEmpty()) {
            return new PayoffProjection(0f, 0, true, 0f);
        }

        int count = orderedDebts.size();
        float[] balance = new float[count];
        float[] monthlyRate = new float[count];
        float[] minimum = new float[count];

        for (int i = 0; i < count; i++) {
            DebtSnapshot debt = orderedDebts.get(i);
            balance[i] = Math.max(0f, debt.currentBalance());
            monthlyRate[i] = AprRates.toMonthlyFromPercent(
                    AprRates.effectivePercent(debt.apr(), debt.estimatedApr()));
            minimum[i] = Math.max(0f, debt.minimumPayment());
        }

        // Apply the lump sum to the payoff target(s), in strategy order.
        float lumpSum = Math.max(0f, lumpSumPayment);
        for (int i = 0; i < count && lumpSum > 0f; i++) {
            float applied = Math.min(balance[i], lumpSum);
            balance[i] -= applied;
            lumpSum -= applied;
        }

        // A closed debt frees its minimum payment for the next target, so the
        // budget is fixed at the start of the plan.
        float budget = Math.max(0f, extraPayment);
        for (int i = 0; i < count; i++) {
            if (balance[i] > 0f) {
                budget += minimum[i];
            }
        }

        float totalInterest = 0f;
        int months = 0;

        while (months < MAX_MONTHS && anyBalance(balance)) {
            float[] interest = new float[count];
            float[] payment = new float[count];
            float paidThisMonth = 0f;

            for (int i = 0; i < count; i++) {
                if (balance[i] <= 0f) {
                    continue;
                }
                interest[i] = balance[i] * monthlyRate[i];
                payment[i] = Math.min(minimum[i], balance[i] + interest[i]);
                paidThisMonth += payment[i];
            }

            // Everything above the minimums attacks the target, in order.
            float remaining = budget - paidThisMonth;
            for (int i = 0; i < count && remaining > 0f; i++) {
                if (balance[i] <= 0f) {
                    continue;
                }
                float outstanding = balance[i] + interest[i] - payment[i];
                if (outstanding <= 0f) {
                    continue;
                }
                float applied = Math.min(outstanding, remaining);
                payment[i] += applied;
                remaining -= applied;
                paidThisMonth += applied;
            }

            for (int i = 0; i < count; i++) {
                if (balance[i] <= 0f) {
                    continue;
                }
                totalInterest += interest[i];
                balance[i] = balance[i] + interest[i] - payment[i];
                if (balance[i] < EPSILON) {
                    balance[i] = 0f;
                }
            }

            months++;

            // A payment that never touches principal would loop until the bound.
            if (paidThisMonth <= 0f) {
                break;
            }
        }

        boolean repaid = !anyBalance(balance);
        return new PayoffProjection(totalInterest, months, repaid, sum(balance));
    }

    private static boolean anyBalance(float[] balances) {
        for (float value : balances) {
            if (value > EPSILON) {
                return true;
            }
        }
        return false;
    }

    private static float sum(float[] values) {
        float total = 0f;
        for (float value : values) {
            total += value;
        }
        return total;
    }
}
