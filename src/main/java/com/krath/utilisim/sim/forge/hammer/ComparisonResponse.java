package com.krath.utilisim.sim.forge.hammer;

import com.krath.utilisim.sim.forge.materials.PlanType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Response for a comparison of up to five plans over one debt set
 * (CURRENT_PLAN_DESIGN §8.2). The recommendation is derived from the returned
 * timelines, not from a separate calculation.
 */
public record ComparisonResponse(
        UUID comparisonGroupId,
        LocalDate startMonth,
        int horizonMonths,
        BigDecimal totalBalance,
        PlanType recommendedPlan,
        String recommendationBasis,
        java.util.List<PlanTimeline> plans
) {
}
