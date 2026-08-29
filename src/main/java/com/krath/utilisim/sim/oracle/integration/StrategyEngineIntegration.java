package com.krath.utilisim.sim.oracle.integration;

import com.krath.utilisim.archive.scenario.ScenarioContext;
import com.krath.utilisim.ledger.tome.Tome;
import com.krath.utilisim.ledger.scribe.Scribe;
import com.krath.utilisim.sim.forge.hammer.SimulationInput;
import com.krath.utilisim.sim.forge.anvil.ForgeSimulationEngine;
import com.krath.utilisim.sim.oracle.*;
import com.krath.utilisim.sim.oracle.vault.*;
import com.krath.utilisim.sim.forge.materials.StrategyType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * Strategy Engine Integration Service
 *
 * Demonstrates how to integrate the Strategy Engine with existing UtiliSim components:
 * - Ledger (Tome, Scribe)
 * - Archive (ScenarioContext)
 * - Forge (ForgeSimulationEngine)
 * - Oracle (StrategyService, OracleService)
 *
 * This service provides high-level methods that orchestrate the entire strategy workflow.
 */
@Service
public class StrategyEngineIntegration {

    @Autowired
    private Scribe scribe;  // Repository for Tome (accounts)

    @Autowired
    private StrategyService strategyService;

    @Autowired
    private OracleService oracleService;

    @Autowired
    private StrategyFactory strategyFactory;

    @Autowired
    private StrategyConfigService strategyConfigService;

    @Autowired
    private StrategyConfigRepository strategyConfigRepository;

    @Autowired
    private StrategyMapper strategyMapper;

    @Autowired
    private ForgeSimulationEngine forgeSimulationEngine;

    /**
     * Workflow 1: Simple Strategy Selection and Simulation
     *
     * Steps:
     * 1. Load user's debts from Ledger
     * 2. Apply selected strategy
     * 3. Save strategy configuration to database
     * 4. Run simulation using Forge
     */
    public StrategySimulationResult runStrategySimulation(
            String userId,
            String scenarioId,
            StrategyType selectedStrategy,
            float extraPayment,
            float lumpSumPayment,
            SimulationInput simulationInput
    ) {
        // Step 1: Load user's debts from Ledger
        List<Tome> userDebts = scribe.findByUserId(userId);
        List<DebtSnapshot> debts = strategyMapper.toDebtSnapshots(userDebts);

        // Step 2: Create strategy input
        StrategyInput strategyInput = new StrategyInput(
            selectedStrategy,
            null,
            extraPayment,
            lumpSumPayment
        );

        // Step 3: Apply strategy ordering
        StrategyOutput strategyOutput = strategyService.orderDebts(debts, strategyInput);

        // Step 4: Save strategy configuration for future reference
        StrategyConfig config = strategyConfigService.createStrategyConfig(
            strategyInput,
            userId,
            scenarioId,
            "Strategy simulation for scenario " + scenarioId
        );

        // Step 5: Create scenario context
        ScenarioContext scenario = new ScenarioContext();
        scenario.setId(scenarioId);
        scenario.setUserId(userId);
        scenario.setStrategy(selectedStrategy.name());
        scenario.setExtraPayment(extraPayment);
        scenario.setLumpSumPayment(lumpSumPayment);

        // Step 6: Run simulation using Forge
        var simulationResult = forgeSimulationEngine.run(simulationInput, scenario);

        // Step 7: Package results
        return new StrategySimulationResult(
            strategyOutput,
            simulationResult,
            config.getId()
        );
    }

    /**
     * Workflow 2: Strategy Recommendation Workflow
     *
     * Steps:
     * 1. Load user's debts
     * 2. Get strategy recommendation
     * 3. Show user the recommendation with analysis
     * 4. Execute recommended strategy
     */
    public StrategyRecommendationResult getStrategyRecommendation(
            String userId,
            SimulationInput simulationInput
    ) {
        // Step 1: Load user's debts
        List<Tome> userDebts = scribe.findByUserId(userId);
        List<DebtSnapshot> debts = strategyMapper.toDebtSnapshots(userDebts);

        // Step 2: Get recommendation
        String recommendedStrategyName = oracleService.recommendOptimalStrategy(debts);

        // Step 3: Create strategy for recommendation
        StrategyInput recommendedStrategy = new StrategyInput(
            StrategyType.valueOf(recommendedStrategyName),
            null,
            simulationInput.getExtraPayment(),
            simulationInput.getLumpSumPayment()
        );

        // Step 4: Apply strategy
        StrategyOutput strategyOutput = strategyService.orderDebts(debts, recommendedStrategy);

        // Step 5: Calculate metrics
        float projectedInterest = strategyService.calculateTotalInterestForOrdering(
            strategyOutput.orderedDebts()
        );

        return new StrategyRecommendationResult(
            recommendedStrategyName,
            strategyOutput.description(),
            projectedInterest,
            strategyOutput.orderedDebts().size()
        );
    }

    /**
     * Workflow 3: Compare Multiple Strategies
     *
     * Steps:
     * 1. Load user's debts
     * 2. Create multiple strategy configurations
     * 3. Analyze each strategy
     * 4. Return comprehensive comparison
     */
    public StrategyComparationResult compareStrategies(
            String userId,
            float extraPayment,
            float lumpSumPayment
    ) {
        // Step 1: Load user's debts
        List<Tome> userDebts = scribe.findByUserId(userId);
        List<DebtSnapshot> debts = strategyMapper.toDebtSnapshots(userDebts);

        // Step 2: Create all strategy options
        List<StrategyInput> strategies = List.of(
            strategyFactory.createSnowball(extraPayment, lumpSumPayment),
            strategyFactory.createAvalanche(extraPayment, lumpSumPayment),
            strategyFactory.createHybrid(extraPayment, lumpSumPayment)
        );

        // Step 3: Create simulation input (using first debt for rate calculation)
        SimulationInput dummy = new SimulationInput();
        dummy.setExtraPayment(extraPayment);
        dummy.setLumpSumPayment(lumpSumPayment);

        // Step 4: Analyze strategies
        StrategyAnalysis analysis = oracleService.analyzeStrategyEffectiveness(
            debts,
            strategies,
            dummy
        );

        return new StrategyComparationResult(
            debts.size(),
            analysis.getComparisons(),
            analysis.getRecommendedStrategy()
        );
    }

    /**
     * Workflow 4: Load and Apply Saved Strategy
     *
     * Steps:
     * 1. Load saved strategy configuration from database
     * 2. Load user's current debts
     * 3. Apply saved strategy
     * 4. Run simulation
     */
    public StrategySimulationResult runSavedStrategy(
            String scenarioId,
            String userId,
            SimulationInput simulationInput
    ) {
        // Step 1: Load saved strategy configuration
        Optional<StrategyConfig> savedConfig = strategyConfigService.getByScenarioId(scenarioId);

        if (savedConfig.isEmpty()) {
            throw new IllegalArgumentException("Strategy configuration not found for scenario: " + scenarioId);
        }

        // Step 2: Convert entity to DTO
        StrategyInput strategyInput = strategyMapper.toStrategyInput(savedConfig.get());

        // Step 3: Load user's current debts
        List<Tome> userDebts = scribe.findByUserId(userId);
        List<DebtSnapshot> debts = strategyMapper.toDebtSnapshots(userDebts);

        // Step 4: Apply saved strategy
        StrategyOutput strategyOutput = strategyService.orderDebts(debts, strategyInput);

        // Step 5: Create scenario context
        ScenarioContext scenario = new ScenarioContext();
        scenario.setId(scenarioId);
        scenario.setUserId(userId);
        scenario.setStrategy(savedConfig.get().getStrategyType().name());
        scenario.setExtraPayment(strategyInput.extraPayment());
        scenario.setLumpSumPayment(strategyInput.lumpSumPayment());

        // Step 6: Run simulation
        var simulationResult = forgeSimulationEngine.run(simulationInput, scenario);

        return new StrategySimulationResult(
            strategyOutput,
            simulationResult,
            savedConfig.get().getId()
        );
    }

    /**
     * Workflow 5: Custom User-Defined Strategy
     *
     * Steps:
     * 1. Load user's debts
     * 2. Apply user's custom ordering
     * 3. Save custom strategy
     * 4. Run simulation
     */
    public StrategySimulationResult runCustomStrategy(
            String userId,
            String scenarioId,
            List<String> customDebtOrder,
            float extraPayment,
            float lumpSumPayment,
            SimulationInput simulationInput
    ) {
        // Step 1: Load user's debts
        List<Tome> userDebts = scribe.findByUserId(userId);
        List<DebtSnapshot> debts = strategyMapper.toDebtSnapshots(userDebts);

        // Step 2: Create custom strategy
        StrategyInput customStrategy = new StrategyInput(
            StrategyType.CUSTOM,
            customDebtOrder,
            extraPayment,
            lumpSumPayment
        );

        // Step 3: Apply custom strategy
        StrategyOutput strategyOutput = strategyService.orderDebts(debts, customStrategy);

        // Step 4: Save custom strategy
        StrategyConfig config = strategyConfigService.createStrategyConfig(
            customStrategy,
            userId,
            scenarioId,
            "Custom user-defined debt ordering"
        );

        // Step 5: Create scenario context
        ScenarioContext scenario = new ScenarioContext();
        scenario.setId(scenarioId);
        scenario.setUserId(userId);
        scenario.setStrategy(StrategyType.CUSTOM.name());
        scenario.setExtraPayment(extraPayment);
        scenario.setLumpSumPayment(lumpSumPayment);

        // Step 6: Run simulation
        var simulationResult = forgeSimulationEngine.run(simulationInput, scenario);

        return new StrategySimulationResult(
            strategyOutput,
            simulationResult,
            config.getId()
        );
    }

    /**
     * Workflow 6: Update Strategy and Resimulate
     *
     * Steps:
     * 1. Load existing strategy
     * 2. Update with new parameters
     * 3. Save updated strategy
     * 4. Run new simulation
     */
    public StrategySimulationResult updateAndResimulate(
            String configId,
            String scenarioId,
            float newExtraPayment,
            float newLumpSum,
            SimulationInput simulationInput
    ) {
        // Step 1: Load existing strategy
        Optional<StrategyConfig> existing = strategyConfigService.getById(configId);

        if (existing.isEmpty()) {
            throw new IllegalArgumentException("Strategy configuration not found");
        }

        StrategyConfig config = existing.get();
        String userId = config.getUserId();

        // Step 2: Create updated strategy input
        StrategyInput updatedInput = new StrategyInput(
            config.getStrategyType(),
            strategyMapper.toStrategyInput(config).customDebtOrder(),
            newExtraPayment,
            newLumpSum
        );

        // Step 3: Save updates
        StrategyConfig updated = strategyConfigService.updateStrategyConfig(
            configId,
            updatedInput,
            config.getDescription() + " (updated)"
        );

        // Step 4: Load debts and apply updated strategy
        List<Tome> userDebts = scribe.findByUserId(userId);
        List<DebtSnapshot> debts = strategyMapper.toDebtSnapshots(userDebts);

        StrategyOutput strategyOutput = strategyService.orderDebts(debts, updatedInput);

        // Step 5: Create scenario and run simulation
        ScenarioContext scenario = new ScenarioContext();
        scenario.setId(scenarioId);
        scenario.setUserId(userId);
        scenario.setStrategy(config.getStrategyType().name());
        scenario.setExtraPayment(newExtraPayment);
        scenario.setLumpSumPayment(newLumpSum);

        var simulationResult = forgeSimulationEngine.run(simulationInput, scenario);

        return new StrategySimulationResult(
            strategyOutput,
            simulationResult,
            updated.getId()
        );
    }

    // ==================== Helper DTOs ====================

    /**
     * Result of running a strategy simulation
     */
    public static class StrategySimulationResult {
        public final StrategyOutput strategyOutput;
        public final Object simulationResult;  // ForgeSimulationEngine result
        public final String strategyConfigId;

        public StrategySimulationResult(
                StrategyOutput strategyOutput,
                Object simulationResult,
                String strategyConfigId
        ) {
            this.strategyOutput = strategyOutput;
            this.simulationResult = simulationResult;
            this.strategyConfigId = strategyConfigId;
        }
    }

    /**
     * Result of strategy recommendation
     */
    public static class StrategyRecommendationResult {
        public final String recommendedStrategy;
        public final String description;
        public final float projectedInterest;
        public final int debtCount;

        public StrategyRecommendationResult(
                String recommendedStrategy,
                String description,
                float projectedInterest,
                int debtCount
        ) {
            this.recommendedStrategy = recommendedStrategy;
            this.description = description;
            this.projectedInterest = projectedInterest;
            this.debtCount = debtCount;
        }
    }

    /**
     * Result of comparing multiple strategies
     */
    public static class StrategyComparationResult {
        public final int debtCount;
        public final List<?> comparisons;  // List<StrategyComparison>
        public final String recommendedStrategy;

        public StrategyComparationResult(
                int debtCount,
                List<?> comparisons,
                String recommendedStrategy
        ) {
            this.debtCount = debtCount;
            this.comparisons = comparisons;
            this.recommendedStrategy = recommendedStrategy;
        }
    }
}
