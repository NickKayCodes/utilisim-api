package com.krath.utilisim.sim.forge.hammer;

import com.krath.utilisim.sim.forge.materials.PlanType;
import com.krath.utilisim.sim.forge.materials.SimulationStatus;
import com.krath.utilisim.sim.forge.materials.TerminationReason;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * One plan's month-by-month result (CURRENT_PLAN_DESIGN §8.2).
 *
 * <p>{@code runId} is the persisted {@code simulation_run.id}; {@code status} is
 * the lifecycle and {@code terminationReason} is the outcome. Both are always
 * returned.</p>
 */
public record PlanTimeline(
        long runId,
        PlanType plan,
        String label,
        SimulationStatus status,
        TerminationReason terminationReason,
        int monthsToPayoff,
        LocalDate payoffDate,
        BigDecimal totalInterest,
        BigDecimal totalPaid,
        BigDecimal finalBalance,
        BigDecimal peakUtilizationPct,
        List<MonthLedger> months
) {
}
