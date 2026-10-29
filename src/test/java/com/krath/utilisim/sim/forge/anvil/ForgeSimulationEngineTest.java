package com.krath.utilisim.sim.forge.anvil;

import com.krath.utilisim.archive.scenario.ScenarioContext;
import com.krath.utilisim.sim.chronos.ChronosService;
import com.krath.utilisim.sim.forge.hammer.SimulationInput;
import com.krath.utilisim.sim.forge.hammer.SimulationResult;
import com.krath.utilisim.sim.forge.materials.SimulationStatus;
import com.krath.utilisim.sim.oracle.OracleService;
import com.krath.utilisim.sim.oracle.StrategyService;
import com.krath.utilisim.sim.ratio.AmortizationEntry;
import com.krath.utilisim.sim.ratio.calculate.RatioService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ForgeSimulationEngineTest {

    private final RatioService ratioService = new RatioService();
    private final ChronosService chronosService = new ChronosService();
    private final OracleService oracleService = new OracleService(ratioService, new StrategyService());
    private final ForgeSimulationEngine engine =
            new ForgeSimulationEngine(ratioService, chronosService, oracleService);

    private SimulationInput input() {
        SimulationInput input = new SimulationInput();
        input.setId("input-1");
        input.setUserId("user-1");
        input.setPrincipal(1200f);
        input.setInterestRate(0.12f);
        input.setTermMonths(24);
        input.setMinimumPayment(100f);
        return input;
    }

    private ScenarioContext scenario(float extra, float lumpSum) {
        ScenarioContext scenario = new ScenarioContext();
        scenario.setExtraPayment(extra);
        scenario.setLumpSumPayment(lumpSum);
        scenario.setStrategy("SNOWBALL");
        return scenario;
    }

    @Test
    @DisplayName("run assembles a completed result from the three engines")
    void runsSimulation() {
        SimulationResult result = engine.run(input(), scenario(50f, 0f));

        assertNotNull(result.getId());
        assertEquals("input-1", result.getSimulationInputId());
        assertEquals("user-1", result.getUserId());
        assertEquals(SimulationStatus.COMPLETED, result.getStatus());
        assertNotNull(result.getStartedAt());
        assertNotNull(result.getCompletedAt());
        assertNotNull(result.getPayoffDate());
        assertFalse(result.getSchedule().isEmpty());
        assertEquals(result.getSchedule().size(), result.getMonthsToPayoff());
        assertEquals(
                result.getSchedule().get(result.getSchedule().size() - 1).getDate(),
                result.getPayoffDate());
        assertEquals(1200f, result.getTotalPrincipalPaid(), 0.01f);
        assertTrue(result.getTotalInterestPaid() > 0);
        assertTrue(result.getSavingsComparedToBaseline() >= 0);
        assertTrue(result.getSchedule().stream().allMatch(entry -> entry.getDate() != null));
    }

    @Test
    @DisplayName("extra payments produce savings against the baseline")
    void reportsSavingsForExtraPayments() {
        SimulationResult result = engine.run(input(), scenario(100f, 0f));

        assertTrue(result.getSavingsComparedToBaseline() > 0);
        assertTrue(result.getMonthsToPayoff() < 24);
    }

    @Test
    @DisplayName("a lump sum that clears the debt completes immediately")
    void handlesClearedBalance() {
        SimulationResult result = engine.run(input(), scenario(0f, 5000f));

        assertEquals(0, result.getMonthsToPayoff());
        assertTrue(result.getSchedule().isEmpty());
        assertEquals(0f, result.getTotalPrincipalPaid());
    }
}
