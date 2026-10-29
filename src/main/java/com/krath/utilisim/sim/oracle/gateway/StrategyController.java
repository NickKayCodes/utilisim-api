package com.krath.utilisim.sim.oracle.gateway;

import com.krath.utilisim.sim.forge.hammer.SimulationInput;
import com.krath.utilisim.sim.forge.materials.StrategyType;
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
     * The request body is a single object. Spring only allows one @RequestBody
     * per handler method, so debts and strategy input are wrapped together.
     *
     * Example:
     * {
     *   "debts": [ { "tomeId": "...", "currentBalance": 1000, ... } ],
     *   "input": { "strategyType": "SNOWBALL", "customDebtOrder": [], "extraPayment": 0, "lumpSumPayment": 0 }
     * }
     */
    @PostMapping("/order")
    public StrategyOutput orderDebts(@RequestBody StrategyOrderRequest request) {
        if (request.input() == null || request.input().strategyType() == null) {
            throw new IllegalArgumentException("input.strategyType is required");
        }
        List<DebtSnapshot> debts = request.debts() == null ? List.of() : request.debts();
        return strategyService.orderDebts(debts, request.input());
    }

    /**
     * Compare total interest for a given debt ordering.
     * Useful for strategy comparison.
     */
    @PostMapping("/calculate-total-interest")
    public float calculateTotalInterest(@RequestBody List<DebtSnapshot> orderedDebts) {
        return strategyService.calculateTotalInterestForOrdering(orderedDebts);
    }

    /**
     * Validate if a strategy type produces deterministic ordering.
     */
    @GetMapping("/is-deterministic/{strategyType}")
    public boolean isDeterministic(@PathVariable String strategyType) {
        try {
            return strategyService.isOrderingDeterministic(StrategyType.valueOf(strategyType));
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    /**
     * Recommend the optimal strategy for a given set of debts.
     */
    @PostMapping("/recommend")
    public String recommendStrategy(@RequestBody List<DebtSnapshot> debts) {
        return oracleService.recommendOptimalStrategy(debts);
    }

    /**
     * Compare multiple strategies for their effectiveness.
     *
     * Example:
     * {
     *   "debts": [ ... ],
     *   "strategies": ["SNOWBALL", "AVALANCHE"],
     *   "input": { "extraPayment": 0, "lumpSumPayment": 0 }
     * }
     */
    @PostMapping("/analyze")
    public StrategyAnalysis analyzeStrategies(@RequestBody StrategyAnalysisRequest request) {
        if (request.strategies() == null || request.strategies().isEmpty()) {
            throw new IllegalArgumentException("strategies must contain at least one strategy name");
        }
        if (request.input() == null) {
            throw new IllegalArgumentException("input is required");
        }

        List<StrategyInput> strategyInputs = request.strategies().stream()
                .map(s -> new StrategyInput(
                        StrategyType.valueOf(s),
                        null,
                        request.input().getExtraPayment(),
                        request.input().getLumpSumPayment()
                ))
                .toList();

        List<DebtSnapshot> debts = request.debts() == null ? List.of() : request.debts();
        return oracleService.analyzeStrategyEffectiveness(debts, strategyInputs, request.input());
    }
}
