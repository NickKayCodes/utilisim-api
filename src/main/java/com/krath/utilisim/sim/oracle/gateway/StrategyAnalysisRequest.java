package com.krath.utilisim.sim.oracle.gateway;

import com.krath.utilisim.sim.forge.hammer.SimulationInput;
import com.krath.utilisim.sim.oracle.DebtSnapshot;

import java.util.List;

/**
 * Single request body for POST /api/oracle/strategy/analyze.
 * Spring does not allow two @RequestBody parameters on one handler.
 */
public record StrategyAnalysisRequest(
        List<DebtSnapshot> debts,
        List<String> strategies,
        SimulationInput input
) {
}
