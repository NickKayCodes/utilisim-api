package com.krath.utilisim.sim.oracle.gateway;

import com.krath.utilisim.sim.oracle.DebtSnapshot;
import com.krath.utilisim.sim.oracle.StrategyInput;

import java.util.List;

/**
 * Single request body for POST /api/oracle/strategy/order.
 * Spring does not allow two @RequestBody parameters on one handler.
 */
public record StrategyOrderRequest(
        List<DebtSnapshot> debts,
        StrategyInput input
) {
}
