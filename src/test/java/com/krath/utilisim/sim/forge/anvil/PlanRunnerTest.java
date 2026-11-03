package com.krath.utilisim.sim.forge.anvil;

import com.krath.utilisim.sim.chronos.ChronosService;
import com.krath.utilisim.sim.forge.hammer.PlanTimeline;
import com.krath.utilisim.sim.forge.materials.PlanType;
import com.krath.utilisim.sim.forge.materials.SimulationStatus;
import com.krath.utilisim.sim.forge.materials.TerminationReason;
import com.krath.utilisim.sim.oracle.StrategyService;
import com.krath.utilisim.sim.ratio.UtilizationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PlanRunnerTest {

    private final ChronosService chronos = new ChronosService();
    private final PayoffLedgerBuilder builder =
            new PayoffLedgerBuilder(new UtilizationService(), chronos);
    private final PlanRunner runner = new PlanRunner(new StrategyService(), builder);

    private DebtInput debt(String id, String balance, String apr, String minimum, String limit) {
        return new DebtInput(
                id, id,
                new BigDecimal(balance),
                new BigDecimal(apr),
                new BigDecimal(minimum),
                new BigDecimal(minimum),
                new BigDecimal(limit));
    }

    private PayoffLedgerBuilder.Params params(int horizon) {
        return new PayoffLedgerBuilder.Params(
                BigDecimal.ZERO, BigDecimal.ZERO, 1, horizon, LocalDate.of(2026, 6, 1));
    }

    @Test
    @DisplayName("a repaid run carries COMPLETED, months, payoff date and totals")
    void mapsACompletedRun() {
        PlanTimeline timeline = runner.run(
                List.of(debt("t1", "1200", "12", "100", "3000")),
                PlanType.CURRENT,
                42L,
                params(60));

        assertEquals(42L, timeline.runId());
        assertEquals(PlanType.CURRENT, timeline.plan());
        assertEquals(SimulationStatus.COMPLETED, timeline.status());
        assertEquals(TerminationReason.PAID_OFF, timeline.terminationReason());
        assertEquals(13, timeline.monthsToPayoff());
        assertEquals(LocalDate.of(2027, 6, 1), timeline.payoffDate());
        assertEquals(new BigDecimal("0.00"), timeline.finalBalance());
        assertTrue(timeline.totalInterest().compareTo(BigDecimal.ZERO) > 0);
        assertTrue(timeline.totalPaid().compareTo(timeline.totalInterest()) > 0);
        assertNotNull(timeline.peakUtilizationPct());
        assertEquals("Jun-26", timeline.months().get(0).monthLabel());
    }

    @Test
    @DisplayName("an unpaid run carries FAILED, zero months and a remaining balance")
    void mapsAFailedRun() {
        PlanTimeline timeline = runner.run(
                List.of(debt("t1", "1000", "18", "10", "2000")),
                PlanType.CURRENT,
                43L,
                params(12));

        assertEquals(SimulationStatus.FAILED, timeline.status());
        assertEquals(TerminationReason.STAGNANT, timeline.terminationReason());
        assertEquals(0, timeline.monthsToPayoff());
        assertNull(timeline.payoffDate());
        assertTrue(timeline.finalBalance().compareTo(BigDecimal.ZERO) > 0);
    }

    @Test
    @DisplayName("a strategy run uses the ORACLE order")
    void strategyRunIsOrdered() {
        DebtInput high = debt("high", "1000", "30", "25", "2000");
        DebtInput low = debt("low", "500", "5", "25", "1000");

        PlanTimeline avalanche = runner.run(List.of(low, high), PlanType.AVALANCHE, 1L,
                new PayoffLedgerBuilder.Params(
                        new BigDecimal("100"), BigDecimal.ZERO, 1, 600,
                        LocalDate.of(2026, 6, 1)));

        // The first debt attacked must be the high-rate one.
        assertEquals("high", avalanche.months().get(0).debts().stream()
                .filter(debtRow -> debtRow.extraPayment().compareTo(BigDecimal.ZERO) > 0)
                .findFirst()
                .orElseThrow()
                .tomeId());
    }
}
