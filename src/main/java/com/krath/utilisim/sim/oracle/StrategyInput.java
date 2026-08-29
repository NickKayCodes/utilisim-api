package com.krath.utilisim.sim.oracle;

import com.krath.utilisim.sim.forge.materials.StrategyType;
import java.util.List;

/**
 * DTO representing strategy input configuration.
 * Contains the strategy type and optional custom ordering.
 */
public record StrategyInput(
    StrategyType strategyType,
    List<String> customDebtOrder,  // For CUSTOM strategy: ordered list of tome IDs
    float extraPayment,             // Recurring extra payment
    float lumpSumPayment            // One-time lump sum payment
) {}

