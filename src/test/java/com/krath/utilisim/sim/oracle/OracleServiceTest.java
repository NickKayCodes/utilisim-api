package com.krath.utilisim.sim.oracle;

import com.krath.utilisim.archive.scenario.ScenarioContext;
import com.krath.utilisim.sim.chronos.projection.SavingsProjection;
import com.krath.utilisim.sim.forge.hammer.SimulationInput;
import com.krath.utilisim.sim.forge.materials.StrategyType;
import com.krath.utilisim.sim.ratio.AmortizationEntry;
import com.krath.utilisim.sim.ratio.calculate.RatioService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OracleServiceTest {

    @Mock
    private RatioService ratioService;

    @Mock
    private StrategyService strategyService;

    private OracleService oracleService;

    @BeforeEach
    void setUp() {
        oracleService = new OracleService(ratioService, strategyService);
    }

    private DebtSnapshot debt(String id, float balance, float apr, float estimatedApr) {
        return new DebtSnapshot(id, id, balance, apr, estimatedApr, 25f, apr);
    }

    @Test
    @DisplayName("compareToBaseline compares against a minimum-only schedule")
    void comparesToBaseline() {
        SimulationInput input = new SimulationInput();
        ScenarioContext scenario = new ScenarioContext();
        scenario.setUserId("u1");
        scenario.setExtraPayment(50f);
        List<AmortizationEntry> scenarioSchedule = List.of(new AmortizationEntry());
        List<AmortizationEntry> baselineSchedule = List.of(new AmortizationEntry());

        when(ratioService.generateSchedule(eq(input), any(ScenarioContext.class)))
                .thenReturn(baselineSchedule);
        when(ratioService.calculateTotalInterest(baselineSchedule)).thenReturn(100f);
        when(ratioService.calculateTotalInterest(scenarioSchedule)).thenReturn(40f);

        SavingsProjection projection = oracleService.compareToBaseline(input, scenario, scenarioSchedule);

        assertEquals(100f, projection.getBaselineInterest());
        assertEquals(40f, projection.getScenarioInterest());
        assertEquals(60f, projection.getSavings());

        ArgumentCaptor<ScenarioContext> captor = ArgumentCaptor.forClass(ScenarioContext.class);
        verify(ratioService).generateSchedule(eq(input), captor.capture());
        ScenarioContext baseline = captor.getValue();
        assertEquals("u1", baseline.getUserId());
        assertEquals(0f, baseline.getExtraPayment());
        assertEquals(0f, baseline.getLumpSumPayment());
        assertEquals("MINIMUM_ONLY", baseline.getStrategy());
    }

    @Test
    @DisplayName("orderDebtsByStrategy delegates to the strategy service")
    void ordersByStrategy() {
        List<DebtSnapshot> debts = List.of(debt("t1", 1000f, 20f, 20f));
        StrategyInput input = new StrategyInput(StrategyType.SNOWBALL, null, 0f, 0f);
        StrategyOutput output = new StrategyOutput("SNOWBALL", debts, "smallest first");
        when(strategyService.orderDebts(debts, input)).thenReturn(output);

        assertSame(output, oracleService.orderDebtsByStrategy(debts, input));
    }

    @Test
    @DisplayName("recommendOptimalStrategy falls back to minimum-only without debts")
    void recommendsMinimumWithoutDebts() {
        assertEquals("MINIMUM_ONLY", oracleService.recommendOptimalStrategy(null));
        assertEquals("MINIMUM_ONLY", oracleService.recommendOptimalStrategy(List.of()));
    }

    @Test
    @DisplayName("recommendOptimalStrategy chooses avalanche for high interest")
    void recommendsAvalancheForHighInterest() {
        assertEquals("AVALANCHE", oracleService.recommendOptimalStrategy(
                List.of(debt("t1", 1000f, 20f, 20f))));
    }

    @Test
    @DisplayName("recommendOptimalStrategy uses estimated APR when APR is missing")
    void recommendsAvalancheUsingEstimatedApr() {
        assertEquals("AVALANCHE", oracleService.recommendOptimalStrategy(
                List.of(debt("t1", 1000f, 0f, 25f))));
    }

    @Test
    @DisplayName("recommendOptimalStrategy chooses snowball for many small low-interest debts")
    void recommendsSnowballForManySmallDebts() {
        List<DebtSnapshot> debts = List.of(
                debt("t1", 500f, 0.05f, 0.05f),
                debt("t2", 600f, 0.06f, 0.06f),
                debt("t3", 700f, 0.07f, 0.07f),
                debt("t4", 800f, 0.08f, 0.08f));

        assertEquals("SNOWBALL", oracleService.recommendOptimalStrategy(debts));
    }

    @Test
    @DisplayName("recommendOptimalStrategy chooses hybrid for a few low-interest debts")
    void recommendsHybridForFewDebts() {
        List<DebtSnapshot> debts = List.of(
                debt("t1", 5000f, 0.05f, 0.05f),
                debt("t2", 6000f, 0.06f, 0.06f));

        assertEquals("HYBRID", oracleService.recommendOptimalStrategy(debts));
    }

    @Test
    @DisplayName("recommendOptimalStrategy chooses avalanche for a single low-interest debt")
    void recommendsAvalancheForSingleDebt() {
        assertEquals("AVALANCHE", oracleService.recommendOptimalStrategy(
                List.of(debt("t1", 5000f, 0.05f, 0.05f))));
    }

    @Test
    @DisplayName("analyzeStrategyEffectiveness ranks strategies by projected interest")
    void analyzesStrategies() {
        List<DebtSnapshot> debts = List.of(debt("t1", 1000f, 20f, 20f));
        StrategyInput snowball = new StrategyInput(StrategyType.SNOWBALL, null, 0f, 0f);
        StrategyInput avalanche = new StrategyInput(StrategyType.AVALANCHE, null, 0f, 0f);
        StrategyOutput snowballOutput = new StrategyOutput("SNOWBALL", debts, "smallest first");
        StrategyOutput avalancheOutput = new StrategyOutput("AVALANCHE", debts, "highest rate first");

        when(strategyService.orderDebts(debts, snowball)).thenReturn(snowballOutput);
        when(strategyService.orderDebts(debts, avalanche)).thenReturn(avalancheOutput);
        when(strategyService.calculateTotalInterestForOrdering(debts)).thenReturn(200f, 100f);

        StrategyAnalysis analysis = oracleService.analyzeStrategyEffectiveness(
                debts, List.of(snowball, avalanche), new SimulationInput());

        assertEquals(2, analysis.getComparisons().size());
        assertEquals("AVALANCHE", analysis.getRecommendedStrategy());
        assertEquals(200f, analysis.getComparisons().get(0).projectedInterest());
        assertEquals(100f, analysis.getComparisons().get(1).projectedInterest());
        assertEquals(1, analysis.getComparisons().get(0).debtCount());
    }

    @Test
    @DisplayName("analyzeStrategyEffectiveness falls back to minimum-only when there is nothing to compare")
    void analyzesNoStrategies() {
        StrategyAnalysis analysis = oracleService.analyzeStrategyEffectiveness(
                List.of(), List.of(), new SimulationInput());

        assertTrue(analysis.getComparisons().isEmpty());
        assertEquals("MINIMUM_ONLY", analysis.getRecommendedStrategy());
    }
}
