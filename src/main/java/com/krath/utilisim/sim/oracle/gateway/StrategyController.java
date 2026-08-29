package com.krath.utilisim.sim.oracle.gateway;

import com.krath.utilisim.sim.forge.hammer.SimulationInput;
import com.krath.utilisim.sim.oracle.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Strategy Controller - Exposes strategy ordering and calculation APIs.
 * Endpoints for ordering debts, comparing strategies, recommending optimal strategies, and validating configurations.
 */
@RestController
@RequestMapping("/api/oracle/strategy")
public class StrategyController {

    @Autowired
    private StrategyService strategyService;

    @Autowired
    private OracleService oracleService;

    /**
     * Order debts based on the specified strategy.
     *
     * @param debts List of debts to order
     * @param input Strategy configuration input
     * @return Ordered debts with strategy metadata
     */
    @PostMapping("/order")
    public StrategyOutput orderDebts(
            @RequestBody List<DebtSnapshot> debts,
            @RequestBody StrategyInput input
    ) {
        return strategyService.orderDebts(debts, input);
    }

    /**
     * Compare total interest for a given debt ordering.
     * Useful for strategy comparison.
     *
     * @param orderedDebts The debts ordered by strategy
     * @return Total interest amount
     */
    @PostMapping("/calculate-total-interest")
    public float calculateTotalInterest(@RequestBody List<DebtSnapshot> orderedDebts) {
        return strategyService.calculateTotalInterestForOrdering(orderedDebts);
    }

    /**
     * Validate if a strategy type produces deterministic ordering.
     *
     * @param strategyType The strategy type to validate
     * @return true if ordering is deterministic, false otherwise
     */
    @GetMapping("/is-deterministic/{strategyType}")
    public boolean isDeterministic(@PathVariable String strategyType) {
        try {
            return strategyService.isOrderingDeterministic(
                com.krath.utilisim.sim.forge.materials.StrategyType.valueOf(strategyType)
            );
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    /**
     * Recommend the optimal strategy for a given set of debts.
     * Analyzes debt characteristics to provide strategy recommendation.
     *
     * @param debts List of debts to analyze
     * @return Recommended strategy type
     */
    @PostMapping("/recommend")
    public String recommendStrategy(@RequestBody List<DebtSnapshot> debts) {
        return oracleService.recommendOptimalStrategy(debts);
    }

    /**
     * Compare multiple strategies for their effectiveness.
     * Analyzes projected outcomes for each strategy.
     *
     * @param debts List of debts
     * @param strategies Strategies to compare
     * @param input Simulation input parameters
     * @return Strategy analysis with comparisons and recommendation
     */
    @PostMapping("/analyze")
    public StrategyAnalysis analyzeStrategies(
            @RequestBody List<DebtSnapshot> debts,
            @RequestParam List<String> strategies,
            @RequestBody SimulationInput input
    ) {
        List<StrategyInput> strategyInputs = strategies.stream()
            .map(s -> new StrategyInput(
                com.krath.utilisim.sim.forge.materials.StrategyType.valueOf(s),
                null,
                input.getExtraPayment(),
                input.getLumpSumPayment()
            ))
            .toList();

        return oracleService.analyzeStrategyEffectiveness(debts, strategyInputs, input);
    }
}


