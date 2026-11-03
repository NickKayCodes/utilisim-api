package com.krath.utilisim.sim.oracle;

import com.krath.utilisim.sim.oracle.DebtProjection.PayoffProjection;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The projection replaces {@code sum(balance * apr)} (DEFECTS API-05/API-17).
 * It must use the right units, react to ordering, and admit when a plan does
 * not pay the debt off.
 */
class DebtProjectionTest {

    private DebtSnapshot debt(String id, float balance, float apr, float minimum) {
        return new DebtSnapshot(id, id, balance, apr, apr, minimum, apr);
    }

    @Test
    @DisplayName("an empty debt list is already repaid")
    void emptyListIsRepaid() {
        PayoffProjection projection = DebtProjection.project(List.of(), 0f, 0f);

        assertEquals(0f, projection.totalInterest());
        assertEquals(0, projection.months());
        assertTrue(projection.repaid());
        assertEquals(0f, projection.remainingBalance());
    }

    @Test
    @DisplayName("a single debt is repaid and the interest is a normal money amount")
    void projectsSingleDebt() {
        PayoffProjection projection = DebtProjection.project(
                List.of(debt("t1", 1200f, 12f, 100f)), 0f, 0f);

        assertTrue(projection.repaid());
        assertEquals(0f, projection.remainingBalance(), 0.01f);
        assertEquals(13, projection.months());
        // Sanity check on units: 84.78 of interest, not 1200 * 12 = 14400.
        assertEquals(84.78f, projection.totalInterest(), 0.5f);
    }

    @Test
    @DisplayName("the old balance-times-APR value is not reproduced")
    void interestIsNotBalanceTimesPercent() {
        PayoffProjection projection = DebtProjection.project(
                List.of(debt("t1", 1000f, 18f, 25f)), 0f, 0f);

        // The pre-fix endpoint returned 18000 for this exact debt.
        assertTrue(projection.totalInterest() < 2000f, projection.toString());
        assertTrue(projection.totalInterest() > 0f);
    }

    @Test
    @DisplayName("with an extra payment, ordering changes the cost: highest rate first is cheaper")
    void orderingChangesInterest() {
        DebtSnapshot highRate = debt("high", 1000f, 30f, 25f);
        DebtSnapshot lowRate = debt("low", 500f, 5f, 25f);

        PayoffProjection avalanche = DebtProjection.project(List.of(highRate, lowRate), 100f, 0f);
        PayoffProjection snowball = DebtProjection.project(List.of(lowRate, highRate), 100f, 0f);

        assertTrue(avalanche.repaid());
        assertTrue(snowball.repaid());
        assertTrue(avalanche.totalInterest() < snowball.totalInterest(),
                "avalanche=" + avalanche.totalInterest() + " snowball=" + snowball.totalInterest());
    }

    @Test
    @DisplayName("without money above the minimums, ordering cannot change the cost")
    void orderingDoesNotChangeCostWithoutExtraMoney() {
        // Every payment is consumed by a minimum, so there is nothing to direct.
        // This is a property of debt, not a bug: a comparison can only differ
        // once the plan has money above the minimums.
        DebtSnapshot highRate = debt("high", 1000f, 30f, 25f);
        DebtSnapshot lowRate = debt("low", 500f, 5f, 25f);

        PayoffProjection avalanche = DebtProjection.project(List.of(highRate, lowRate), 0f, 0f);
        PayoffProjection snowball = DebtProjection.project(List.of(lowRate, highRate), 0f, 0f);

        assertEquals(avalanche.totalInterest(), snowball.totalInterest(), 0.01f);
    }

    @Test
    @DisplayName("a plan whose payments never cover interest is not repaid")
    void reportsUnpaidPlan() {
        PayoffProjection projection = DebtProjection.project(
                List.of(debt("t1", 1000f, 0f, 0f)), 0f, 0f);

        assertFalse(projection.repaid());
        assertEquals(1000f, projection.remainingBalance(), 0.01f);
        assertEquals(0f, projection.totalInterest(), 0.01f);
    }

    @Test
    @DisplayName("an extra payment shortens the plan and lowers interest")
    void extraPaymentHelps() {
        List<DebtSnapshot> debts = List.of(debt("t1", 1200f, 12f, 100f));

        PayoffProjection baseline = DebtProjection.project(debts, 0f, 0f);
        PayoffProjection withExtra = DebtProjection.project(debts, 50f, 0f);

        assertTrue(withExtra.months() < baseline.months());
        assertTrue(withExtra.totalInterest() < baseline.totalInterest());
    }

    @Test
    @DisplayName("a lump sum attacks the target first and lowers interest")
    void lumpSumHelps() {
        List<DebtSnapshot> debts = List.of(debt("t1", 1200f, 12f, 100f));

        PayoffProjection baseline = DebtProjection.project(debts, 0f, 0f);
        PayoffProjection withLumpSum = DebtProjection.project(debts, 0f, 200f);

        assertTrue(withLumpSum.totalInterest() < baseline.totalInterest());
        assertTrue(withLumpSum.months() < baseline.months());
    }

    @Test
    @DisplayName("debts that are already at zero cost nothing")
    void zeroBalancesAreRepaid() {
        PayoffProjection projection = DebtProjection.project(
                List.of(debt("t1", 0f, 20f, 25f), debt("t2", 0f, 5f, 10f)), 0f, 0f);

        assertTrue(projection.repaid());
        assertEquals(0, projection.months());
        assertEquals(0f, projection.totalInterest());
    }

    @Test
    @DisplayName("the projection is deterministic")
    void isDeterministic() {
        List<DebtSnapshot> debts = List.of(
                debt("a", 900f, 24f, 30f),
                debt("b", 400f, 5f, 20f));

        PayoffProjection first = DebtProjection.project(debts, 25f, 100f);
        PayoffProjection second = DebtProjection.project(debts, 25f, 100f);

        assertEquals(first, second);
    }
}
