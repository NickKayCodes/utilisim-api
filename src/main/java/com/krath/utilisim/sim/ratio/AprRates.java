package com.krath.utilisim.sim.ratio;

/**
 * One place for APR unit conversion.
 *
 * <p>UtiliSim uses two representations and mixing them is the cause of the
 * 100× interest bug (DEFECTS API-05) and the inert recommendation threshold
 * (API-06):</p>
 *
 * <ul>
 *   <li><b>Percent</b> — what clients send and see: {@code 19.99} means 19.99%.
 *       Used by {@code Tome.apr}, {@code Tome.estimatedApr} and
 *       {@code DebtSnapshot.apr}/{@code estimatedApr}.</li>
 *   <li><b>Decimal annual</b> — what the amortization math needs: {@code 0.1999}.
 *       Used by {@code SimulationInput.interestRate}.</li>
 * </ul>
 *
 * <p>Never multiply a balance by a percentage rate directly; convert first.</p>
 */
public final class AprRates {

    /** Percent -> decimal divisor. */
    public static final float PERCENT_DIVISOR = 100f;

    public static final int MONTHS_PER_YEAR = 12;

    private AprRates() {
    }

    /** {@code 19.99} -> {@code 0.1999}. */
    public static float toDecimal(float annualPercent) {
        return annualPercent / PERCENT_DIVISOR;
    }

    /** {@code 19.99} -> {@code 0.016658...} (monthly decimal). */
    public static float toMonthlyFromPercent(float annualPercent) {
        return annualPercent / PERCENT_DIVISOR / MONTHS_PER_YEAR;
    }

    /** {@code 0.1999} -> {@code 0.016658...} (monthly decimal). */
    public static float toMonthlyFromDecimal(float annualDecimal) {
        return annualDecimal / MONTHS_PER_YEAR;
    }

    /**
     * The rate to charge a debt, still as a percent.
     *
     * <p>A missing APR is represented as {@code 0}; in that case the stored
     * estimate is used. Both values are percentages, so they are directly
     * comparable.</p>
     */
    public static float effectivePercent(float aprPercent, float estimatedAprPercent) {
        return aprPercent > 0f ? aprPercent : estimatedAprPercent;
    }
}
