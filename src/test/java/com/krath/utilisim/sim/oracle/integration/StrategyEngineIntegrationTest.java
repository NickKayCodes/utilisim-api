package com.krath.utilisim.sim.oracle.integration;

import com.krath.utilisim.archive.scenario.ScenarioContext;
import com.krath.utilisim.ledger.scribe.Scribe;
import com.krath.utilisim.ledger.tome.Tome;
import com.krath.utilisim.sim.forge.anvil.ForgeSimulationEngine;
import com.krath.utilisim.sim.forge.hammer.SimulationInput;
import com.krath.utilisim.sim.forge.hammer.SimulationResult;
import com.krath.utilisim.sim.forge.materials.StrategyType;
import com.krath.utilisim.sim.oracle.*;
import com.krath.utilisim.sim.oracle.vault.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class StrategyEngineIntegrationTest {

    @Mock
    private Scribe scribe;

    @Mock
    private StrategyService strategyService;

    @Mock
    private OracleService oracleService;

    @Mock
    private StrategyFactory strategyFactory;

    @Mock
    private StrategyConfigService strategyConfigService;

    @Mock
    private StrategyConfigRepository strategyConfigRepository;

    @Mock
    private StrategyMapper strategyMapper;

    @Mock
    private ForgeSimulationEngine forgeSimulationEngine;

    @InjectMocks
    private StrategyEngineIntegration integration;

    private final Tome tome = new Tome();
    private final DebtSnapshot debt = new DebtSnapshot(
            "t1", "Visa", 1200f, 22f, 22f, 35f, 22f);

    private SimulationInput input() {
        SimulationInput input = new SimulationInput();
        input.setExtraPayment(50f);
        input.setLumpSumPayment(100f);
        return input;
    }

    private StrategyConfig config(String id) {
        StrategyConfig config = new StrategyConfig();
        config.setId(id);
        config.setUserId("u1");
        config.setScenarioId("s1");
        config.setStrategyType(StrategyType.SNOWBALL);
        config.setDescription("why");
        return config;
    }

    @BeforeEach
    void setUp() {
        when(scribe.findByUserId("u1")).thenReturn(List.of(tome));
        when(strategyMapper.toDebtSnapshots(List.of(tome))).thenReturn(List.of(debt));
    }

    @Test
    @DisplayName("runStrategySimulation orders debts, saves config and runs the engine")
    void runsStrategySimulation() {
        StrategyOutput output = new StrategyOutput("SNOWBALL", List.of(debt), "smallest first");
        StrategyConfig config = config("cfg-1");
        SimulationResult simulationResult = new SimulationResult();

        when(strategyService.orderDebts(eq(List.of(debt)), any(StrategyInput.class))).thenReturn(output);
        when(strategyConfigService.createStrategyConfig(
                any(StrategyInput.class), eq("u1"), eq("s1"), anyString())).thenReturn(config);
        when(forgeSimulationEngine.run(any(SimulationInput.class), any(ScenarioContext.class))).thenReturn(simulationResult);

        var result = integration.runStrategySimulation(
                "u1", "s1", StrategyType.SNOWBALL, 50f, 100f, input());

        assertSame(output, result.strategyOutput);
        assertSame(simulationResult, result.simulationResult);
        assertEquals("cfg-1", result.strategyConfigId);

        ArgumentCaptor<ScenarioContext> captor = ArgumentCaptor.forClass(ScenarioContext.class);
        verify(forgeSimulationEngine).run(any(SimulationInput.class), captor.capture());
        assertEquals("s1", captor.getValue().getId());
        assertEquals("u1", captor.getValue().getUserId());
        assertEquals("SNOWBALL", captor.getValue().getStrategy());
        assertEquals(50f, captor.getValue().getExtraPayment());
        assertEquals(100f, captor.getValue().getLumpSumPayment());
    }

    @Test
    @DisplayName("getStrategyRecommendation applies the recommended strategy")
    void recommendsStrategy() {
        StrategyOutput output = new StrategyOutput("AVALANCHE", List.of(debt), "highest rate first");

        when(oracleService.recommendOptimalStrategy(List.of(debt))).thenReturn("AVALANCHE");
        when(strategyService.orderDebts(eq(List.of(debt)), any(StrategyInput.class))).thenReturn(output);
        when(strategyService.calculateTotalInterestForOrdering(List.of(debt))).thenReturn(250f);

        var result = integration.getStrategyRecommendation("u1", input());

        assertEquals("AVALANCHE", result.recommendedStrategy);
        assertEquals("highest rate first", result.description);
        assertEquals(250f, result.projectedInterest);
        assertEquals(1, result.debtCount);
    }

    @Test
    @DisplayName("compareStrategies compares snowball, avalanche and hybrid")
    void comparesStrategies() {
        StrategyInput snowball = new StrategyInput(StrategyType.SNOWBALL, null, 50f, 100f);
        StrategyInput avalanche = new StrategyInput(StrategyType.AVALANCHE, null, 50f, 100f);
        StrategyInput hybrid = new StrategyInput(StrategyType.HYBRID, null, 50f, 100f);
        StrategyComparison comparison = new StrategyComparison("SNOWBALL", 100f, 1, "a");
        StrategyAnalysis analysis = new StrategyAnalysis(List.of(comparison), "SNOWBALL");

        when(strategyFactory.createSnowball(50f, 100f)).thenReturn(snowball);
        when(strategyFactory.createAvalanche(50f, 100f)).thenReturn(avalanche);
        when(strategyFactory.createHybrid(50f, 100f)).thenReturn(hybrid);
        when(oracleService.analyzeStrategyEffectiveness(
                eq(List.of(debt)), anyList(), any(SimulationInput.class))).thenReturn(analysis);

        var result = integration.compareStrategies("u1", 50f, 100f);

        assertEquals(1, result.debtCount);
        assertEquals(1, result.comparisons.size());
        assertEquals("SNOWBALL", result.recommendedStrategy);
    }

    @Test
    @DisplayName("runSavedStrategy loads the saved configuration and runs it")
    void runsSavedStrategy() {
        StrategyConfig config = config("cfg-1");
        config.setStrategyType(StrategyType.AVALANCHE);
        StrategyInput savedInput = new StrategyInput(StrategyType.AVALANCHE, List.of("t1"), 20f, 30f);
        StrategyOutput output = new StrategyOutput("AVALANCHE", List.of(debt), "highest rate first");
        SimulationResult simulationResult = new SimulationResult();

        when(strategyConfigService.getByScenarioId("s1")).thenReturn(Optional.of(config));
        when(strategyMapper.toStrategyInput(config)).thenReturn(savedInput);
        when(strategyService.orderDebts(eq(List.of(debt)), eq(savedInput))).thenReturn(output);
        when(forgeSimulationEngine.run(any(SimulationInput.class), any(ScenarioContext.class))).thenReturn(simulationResult);

        var result = integration.runSavedStrategy("s1", "u1", input());

        assertSame(output, result.strategyOutput);
        assertEquals("cfg-1", result.strategyConfigId);

        ArgumentCaptor<ScenarioContext> captor = ArgumentCaptor.forClass(ScenarioContext.class);
        verify(forgeSimulationEngine).run(any(SimulationInput.class), captor.capture());
        assertEquals("AVALANCHE", captor.getValue().getStrategy());
        assertEquals(20f, captor.getValue().getExtraPayment());
        assertEquals(30f, captor.getValue().getLumpSumPayment());
    }

    @Test
    @DisplayName("runSavedStrategy rejects a missing configuration")
    void rejectsMissingSavedStrategy() {
        when(strategyConfigService.getByScenarioId("s1")).thenReturn(Optional.empty());

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> integration.runSavedStrategy("s1", "u1", input()));

        assertEquals("Strategy configuration not found for scenario: s1", ex.getMessage());
        verifyNoInteractions(forgeSimulationEngine);
    }

    @Test
    @DisplayName("runCustomStrategy applies and saves a custom order")
    void runsCustomStrategy() {
        StrategyOutput output = new StrategyOutput("CUSTOM", List.of(debt), "custom");
        StrategyConfig config = config("cfg-2");
        SimulationResult simulationResult = new SimulationResult();

        when(strategyService.orderDebts(eq(List.of(debt)), any(StrategyInput.class))).thenReturn(output);
        when(strategyConfigService.createStrategyConfig(
                any(StrategyInput.class), eq("u1"), eq("s1"), anyString())).thenReturn(config);
        when(forgeSimulationEngine.run(any(SimulationInput.class), any(ScenarioContext.class))).thenReturn(simulationResult);

        var result = integration.runCustomStrategy(
                "u1", "s1", List.of("t1"), 50f, 100f, input());

        assertSame(output, result.strategyOutput);
        assertEquals("cfg-2", result.strategyConfigId);

        verify(strategyService).orderDebts(
                eq(List.of(debt)),
                argThat(strategy -> strategy.strategyType() == StrategyType.CUSTOM
                        && strategy.customDebtOrder().equals(List.of("t1"))));
    }

    @Test
    @DisplayName("updateAndResimulate rejects a missing configuration")
    void rejectsMissingConfigOnUpdate() {
        when(strategyConfigService.getById("cfg-1")).thenReturn(Optional.empty());

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> integration.updateAndResimulate("cfg-1", "s1", 10f, 20f, input()));

        assertEquals("Strategy configuration not found", ex.getMessage());
    }

    @Test
    @DisplayName("updateAndResimulate updates the configuration and runs a new simulation")
    void updatesAndResimulates() {
        StrategyConfig existing = config("cfg-1");
        StrategyConfig updated = config("cfg-1");
        StrategyInput stored = new StrategyInput(StrategyType.SNOWBALL, List.of("t1"), 0f, 0f);
        StrategyOutput output = new StrategyOutput("SNOWBALL", List.of(debt), "smallest first");
        SimulationResult simulationResult = new SimulationResult();

        when(strategyConfigService.getById("cfg-1")).thenReturn(Optional.of(existing));
        when(strategyMapper.toStrategyInput(existing)).thenReturn(stored);
        when(strategyConfigService.updateStrategyConfig(
                eq("cfg-1"), any(StrategyInput.class), eq("why (updated)"))).thenReturn(updated);
        when(strategyService.orderDebts(eq(List.of(debt)), any(StrategyInput.class))).thenReturn(output);
        when(forgeSimulationEngine.run(any(SimulationInput.class), any(ScenarioContext.class))).thenReturn(simulationResult);

        var result = integration.updateAndResimulate("cfg-1", "s1", 75f, 125f, input());

        assertSame(output, result.strategyOutput);
        assertEquals("cfg-1", result.strategyConfigId);

        ArgumentCaptor<StrategyInput> inputCaptor = ArgumentCaptor.forClass(StrategyInput.class);
        verify(strategyConfigService).updateStrategyConfig(
                eq("cfg-1"), inputCaptor.capture(), eq("why (updated)"));
        assertEquals(75f, inputCaptor.getValue().extraPayment());
        assertEquals(125f, inputCaptor.getValue().lumpSumPayment());
        assertEquals(List.of("t1"), inputCaptor.getValue().customDebtOrder());
    }
}
