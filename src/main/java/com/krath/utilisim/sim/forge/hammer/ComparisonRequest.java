package com.krath.utilisim.sim.forge.hammer;

import com.krath.utilisim.sim.forge.materials.PlanType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Request for {@code POST /api/simulate/compare} (CURRENT_PLAN_DESIGN §8.1).
 *
 * <p>{@code tomeIds} empty or null means "all of the caller's accounts".
 * {@code plans} defaults to all five in the service.</p>
 */
public record ComparisonRequest(
        List<String> tomeIds,
        BigDecimal extraPayment,
        BigDecimal lumpSumPayment,
        Integer lumpSumMonth,
        LocalDate startMonth,
        Integer horizonMonths,
        List<PlanType> plans
) {
}
