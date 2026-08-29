package com.krath.utilisim.sim.oracle;

import java.util.List;

/**
 * DTO representing the result of strategy ordering.
 * Contains the ordered list of debts and metadata about the ordering.
 */
public record StrategyOutput(
    String strategyName,              // e.g., "SNOWBALL", "AVALANCHE", "HYBRID", "CUSTOM"
    List<DebtSnapshot> orderedDebts,  // Debts ordered by the applied strategy
    String description                // Strategy description/explanation
) {}

