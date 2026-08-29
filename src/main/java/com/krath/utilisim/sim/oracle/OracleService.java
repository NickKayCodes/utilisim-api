package com.krath.utilisim.sim.oracle;

import com.krath.utilisim.archive.scenario.ScenarioContext;
import com.krath.utilisim.sim.chronos.projection.SavingsProjection;
import com.krath.utilisim.sim.forge.hammer.SimulationInput;
import com.krath.utilisim.sim.ratio.AmortizationEntry;
import com.krath.utilisim.sim.ratio.calculate.RatioService;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Oracle Service - Strategy and comparison engine.
 * Handles strategy ordering, baseline comparisons, and financial projections.
 * Integrates StrategyService for debt ordering.
 */
@Service
public class OracleService {

    private final RatioService ratioService;
    private final StrategyService strategyService;

    public OracleService(RatioService ratioService, StrategyService strategyService) {
        this.ratioService = ratioService;
        this.strategyService = strategyService;
    }

    public SavingsProjection compareToBaseline(
            SimulationInput input,
            ScenarioContext scenario,
            List<AmortizationEntry> scenarioSchedule
    ) {

        // Baseline = minimum payment only
        ScenarioContext baselineScenario = new ScenarioContext();
        baselineScenario.setUserId(scenario.getUserId());
        baselineScenario.setExtraPayment(0f);
        baselineScenario.setLumpSumPayment(0f);
        baselineScenario.setStrategy("MINIMUM_ONLY");

        // Generate baseline schedule
        List<AmortizationEntry> baselineSchedule =
                ratioService.generateSchedule(input, baselineScenario);

        // Calculate interest totals
        float baselineInterest = ratioService.calculateTotalInterest(baselineSchedule);
        float scenarioInterest = ratioService.calculateTotalInterest(scenarioSchedule);

        // Savings = baseline - scenario
        float savings = baselineInterest - scenarioInterest;

        SavingsProjection projection = new SavingsProjection();
        projection.setBaselineInterest(baselineInterest);
        projection.setScenarioInterest(scenarioInterest);
        projection.setSavings(savings);

        return projection;
    }

    /**
     * Order debts according to the specified strategy.
     * Delegates to StrategyService for ordering logic.
     */
    public StrategyOutput orderDebtsByStrategy(List<DebtSnapshot> debts, StrategyInput input) {
        return strategyService.orderDebts(debts, input);
    }

    /**
     * Determine the optimal strategy for a given set of debts.
     * Analyzes debt characteristics to recommend best strategy.
     */
    public String recommendOptimalStrategy(List<DebtSnapshot> debts) {
        if (debts == null || debts.isEmpty()) {
            return "MINIMUM_ONLY";
        }

        // Calculate metrics
        float maxInterestRate = debts.stream()
            .map(d -> d.apr() > 0 ? d.apr() : d.estimatedApr())
            .max(Float::compare)
            .orElse(0f);

        float minBalance = debts.stream()
            .map(DebtSnapshot::currentBalance)
            .min(Float::compare)
            .orElse(0f);

        int debtCount = debts.size();

        // Recommendation logic
        if (maxInterestRate > 0.15f) {
            // High interest: Avalanche is mathematically better
            return "AVALANCHE";
        } else if (debtCount > 3 && minBalance < 1000) {
            // Multiple debts with manageable balances: Snowball for psychology
            return "SNOWBALL";
        } else if (debtCount > 1) {
            // Balanced approach for multiple debts
            return "HYBRID";
        } else {
            // Single debt: Any strategy is same
            return "AVALANCHE";
        }
    }

    /**
     * Analyze strategy effectiveness by comparing projected outcomes.
     */
    public StrategyAnalysis analyzeStrategyEffectiveness(
            List<DebtSnapshot> debts,
            List<StrategyInput> strategiesToCompare,
            SimulationInput input
    ) {
        StrategyAnalysis analysis = new StrategyAnalysis();
        List<StrategyComparison> comparisons = new java.util.ArrayList<>();

        for (StrategyInput strategy : strategiesToCompare) {
            StrategyOutput ordering = strategyService.orderDebts(debts, strategy);
            float totalInterest = strategyService.calculateTotalInterestForOrdering(ordering.orderedDebts());

            comparisons.add(new StrategyComparison(
                ordering.strategyName(),
                totalInterest,
                ordering.orderedDebts().size(),
                ordering.description()
            ));
        }

        analysis.setComparisons(comparisons);
        analysis.setRecommendedStrategy(
            comparisons.stream()
                .min((c1, c2) -> Float.compare(c1.projectedInterest(), c2.projectedInterest()))
                .map(StrategyComparison::strategyName)
                .orElse("MINIMUM_ONLY")
        );

        return analysis;
    }
}
