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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OracleServiceTest {

    @Mock
    private RatioService ratioService;

    @Mock
    private StrategyService strategyService;

    private OracleService oracleService;
    private OracleService realOracle;

    @BeforeEach
    void setUp() {
        oracleService = new OracleService(ratioService, strategyService);
        // Recommendation and analysis delegate to real ordering + projection.
        realOracle = new OracleService(ratioService, new StrategyService());
    }

    private DebtSnapshot debt(String id, float balance, float apr, float estimatedApr) {
        return new DebtSnapshot(id, id, balance, apr, estimatedApr, 25f, apr);
    }

    private DebtSnapshot debt(String id, float balance, float apr, float estimatedApr, float minimum) {
        return new DebtSnapshot(id, id, balance, apr, estimatedApr, minimum, apr);
    }

    private List<StrategyInput> candidates() {
        return OracleService.RECOMMENDATION_CANDIDATES.stream()
                .map(type -> new StrategyInput(type, null, 0f, 0f))
                .toList();
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
        assertEquals("MINIMUM_ONLY", realOracle.recommendOptimalStrategy(null));
        assertEquals("MINIMUM_ONLY", realOracle.recommendOptimalStrategy(List.of()));
    }

    @Test
    @DisplayName("recommendOptimalStrategy falls back to minimum-only when nothing is owed")
    void recommendsMinimumWithoutBalance() {
        assertEquals("MINIMUM_ONLY", realOracle.recommendOptimalStrategy(List.of(
                debt("t1", 0f, 20f, 20f),
                debt("t2", 0f, 5f, 5f))));
    }

    @Test
    @DisplayName("recommendOptimalStrategy chooses avalanche for a high-interest debt")
    void recommendsAvalancheForHighInterest() {
        assertEquals("AVALANCHE", realOracle.recommendOptimalStrategy(
                List.of(debt("t1", 1000f, 20f, 20f))));
    }

    @Test
    @DisplayName("recommendOptimalStrategy uses estimated APR when APR is missing")
    void recommendsAvalancheUsingEstimatedApr() {
        assertEquals("AVALANCHE", realOracle.recommendOptimalStrategy(
                List.of(debt("t1", 1000f, 0f, 25f))));
    }

    @Test
    @DisplayName("/recommend and /analyze return the same strategy for many debts")
    void recommendationAgreesWithAnalysis() {
        List<DebtSnapshot> debts = List.of(
                debt("t1", 500f, 5f, 5f),
                debt("t2", 600f, 6f, 6f),
                debt("t3", 700f, 7f, 7f),
                debt("t4", 800f, 8f, 8f));

        String recommended = realOracle.recommendOptimalStrategy(debts);
        StrategyAnalysis analysis = realOracle.analyzeStrategyEffectiveness(
                debts, candidates(), new SimulationInput());

        assertEquals(analysis.getRecommendedStrategy(), recommended);
        assertTrue(analysis.getComparisons().stream()
                .anyMatch(comparison -> comparison.strategyName().equals(recommended)));
    }

    @Test
    @DisplayName("/recommend and /analyze agree when every rate is identical")
    void recommendationAgreesOnEqualRates() {
        List<DebtSnapshot> debts = List.of(
                debt("t1", 1000f, 10f, 10f),
                debt("t2", 400f, 10f, 10f),
                debt("t3", 2200f, 10f, 10f));

        String first = realOracle.recommendOptimalStrategy(debts);
        String second = realOracle.recommendOptimalStrategy(debts);
        StrategyAnalysis analysis = realOracle.analyzeStrategyEffectiveness(
                debts, candidates(), new SimulationInput());

        assertEquals(first, second, "ties must not depend on request order");
        assertEquals(analysis.getRecommendedStrategy(), first);
    }

    @Test
    @DisplayName("analyze ranks strategies by their projected interest")
    void ranksStrategiesByProjectedInterest() {
        List<DebtSnapshot> debts = List.of(
                debt("t1", 2000f, 25f, 25f, 40f),
                debt("t2", 500f, 5f, 5f, 20f));
        SimulationInput input = new SimulationInput();
        input.setExtraPayment(200f);

        StrategyAnalysis analysis = realOracle.analyzeStrategyEffectiveness(
                debts, candidates(), input);

        assertEquals(3, analysis.getComparisons().size());
        assertEquals("AVALANCHE", analysis.getRecommendedStrategy());

        float avalanche = analysis.getComparisons().stream()
                .filter(c -> c.strategyName().equals("AVALANCHE"))
                .findFirst().orElseThrow().projectedInterest();
        float snowball = analysis.getComparisons().stream()
                .filter(c -> c.strategyName().equals("SNOWBALL"))
                .findFirst().orElseThrow().projectedInterest();

        assertTrue(avalanche < snowball, "avalanche=" + avalanche + " snowball=" + snowball);
        assertEquals(2, analysis.getComparisons().get(0).debtCount());
    }

    @Test
    @DisplayName("analyze actually reads input.extraPayment")
    void analyzeUsesInput() {
        List<DebtSnapshot> debts = List.of(
                debt("t1", 2000f, 25f, 25f, 40f),
                debt("t2", 500f, 5f, 5f, 20f));

        StrategyAnalysis withoutExtra = realOracle.analyzeStrategyEffectiveness(
                debts, candidates(), new SimulationInput());
        SimulationInput withExtraInput = new SimulationInput();
        withExtraInput.setExtraPayment(300f);
        StrategyAnalysis withExtra = realOracle.analyzeStrategyEffectiveness(
                debts, candidates(), withExtraInput);

        float slow = withoutExtra.getComparisons().get(0).projectedInterest();
        float fast = withExtra.getComparisons().get(0).projectedInterest();

        assertTrue(fast < slow, "extra payments must reduce projected interest");
    }

    @Test
    @DisplayName("analyze treats a null input as no extra payments")
    void analyzeHandlesNullInput() {
        List<DebtSnapshot> debts = List.of(debt("t1", 1000f, 12f, 12f));

        StrategyAnalysis analysis = realOracle.analyzeStrategyEffectiveness(debts, candidates(), null);

        assertEquals(3, analysis.getComparisons().size());
        assertNotNull(analysis.getRecommendedStrategy());
    }

    @Test
    @DisplayName("analyzeStrategyEffectiveness falls back to minimum-only when there is nothing to compare")
    void analyzesNoStrategies() {
        StrategyAnalysis analysis = realOracle.analyzeStrategyEffectiveness(
                List.of(), List.of(), new SimulationInput());

        assertTrue(analysis.getComparisons().isEmpty());
        assertEquals("MINIMUM_ONLY", analysis.getRecommendedStrategy());
    }
}
