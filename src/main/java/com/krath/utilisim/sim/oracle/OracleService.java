package com.krath.utilisim.sim.oracle;

import com.krath.utilisim.archive.scenario.ScenarioContext;
import com.krath.utilisim.sim.chronos.projection.SavingsProjection;
import com.krath.utilisim.sim.forge.hammer.SimulationInput;
import com.krath.utilisim.sim.forge.materials.StrategyType;
import com.krath.utilisim.sim.ratio.AmortizationEntry;
import com.krath.utilisim.sim.ratio.calculate.RatioService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Oracle Service - Strategy and comparison engine.
 * Handles strategy ordering, baseline comparisons, and financial projections.
 * Integrates StrategyService for debt ordering.
 */
@Service
public class OracleService {

    /**
     * Strategies `/recommend` is allowed to choose from.
     *
     * <p>CUSTOM needs a client-supplied order and MINIMUM_ONLY is the absence of
     * a strategy, so neither is a recommendation candidate.</p>
     */
    static final List<StrategyType> RECOMMENDATION_CANDIDATES = List.of(
            StrategyType.SNOWBALL,
            StrategyType.AVALANCHE,
            StrategyType.HYBRID
    );

    /**
     * Deterministic ranking used by both endpoints.
     *
     * <ol>
     *   <li>Plans that actually clear the debt beat plans that do not.</li>
     *   <li>Lower projected interest wins.</li>
     *   <li>Then the shorter payoff.</li>
     *   <li>Then the strategy name, so identical outcomes never depend on
     *       request order (the old tie-break was array order, API-17).</li>
     * </ol>
     */
    private static final Comparator<Ranked> RANKING = Comparator
            .comparing((Ranked ranked) -> !ranked.projection().repaid())
            .thenComparing(ranked -> ranked.projection().totalInterest())
            .thenComparing(ranked -> ranked.projection().months())
            .thenComparing(Ranked::name);

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
     * Determine the optimal strategy for a set of debts.
     *
     * <p>This runs the same projection and the same ranking as
     * {@code /analyze}, so the two endpoints cannot disagree (API-17). It assumes
     * no extra payments, matching the request shape of {@code /recommend}; send
     * extra payments to {@code /analyze} instead.</p>
     */
    public String recommendOptimalStrategy(List<DebtSnapshot> debts) {
        if (debts == null || debts.isEmpty()) {
            return "MINIMUM_ONLY";
        }
        boolean hasBalance = debts.stream().anyMatch(debt -> debt.currentBalance() > 0f);
        if (!hasBalance) {
            return "MINIMUM_ONLY";
        }

        List<StrategyInput> candidates = RECOMMENDATION_CANDIDATES.stream()
                .map(type -> new StrategyInput(type, null, 0f, 0f))
                .toList();

        return analyzeStrategyEffectiveness(debts, candidates, new SimulationInput())
                .getRecommendedStrategy();
    }

    /**
     * Analyze strategy effectiveness by comparing projected payoffs.
     *
     * <p>The projection is ordering-aware ({@link DebtProjection}), so different
     * strategies now produce different numbers, and {@code input.extraPayment} /
     * {@code input.lumpSumPayment} genuinely affect the result.</p>
     */
    public StrategyAnalysis analyzeStrategyEffectiveness(
            List<DebtSnapshot> debts,
            List<StrategyInput> strategiesToCompare,
            SimulationInput input
    ) {
        StrategyAnalysis analysis = new StrategyAnalysis();
        List<StrategyComparison> comparisons = new ArrayList<>();
        List<Ranked> ranked = new ArrayList<>();

        float extraPayment = input == null ? 0f : input.getExtraPayment();
        float lumpSumPayment = input == null ? 0f : input.getLumpSumPayment();

        for (StrategyInput strategy : strategiesToCompare) {
            StrategyOutput ordering = strategyService.orderDebts(debts, strategy);
            DebtProjection.PayoffProjection projection = DebtProjection.project(
                    ordering.orderedDebts(), extraPayment, lumpSumPayment);

            comparisons.add(new StrategyComparison(
                    ordering.strategyName(),
                    projection.totalInterest(),
                    ordering.orderedDebts().size(),
                    ordering.description()
            ));
            ranked.add(new Ranked(ordering.strategyName(), projection));
        }

        analysis.setComparisons(comparisons);
        analysis.setRecommendedStrategy(
                ranked.stream()
                        .min(RANKING)
                        .map(Ranked::name)
                        .orElse("MINIMUM_ONLY")
        );

        return analysis;
    }

    /** A strategy name paired with its projected outcome, for ranking. */
    private record Ranked(String name, DebtProjection.PayoffProjection projection) {
    }
}
