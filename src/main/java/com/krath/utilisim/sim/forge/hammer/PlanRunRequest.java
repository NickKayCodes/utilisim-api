package com.krath.utilisim.sim.forge.hammer;

import com.krath.utilisim.sim.forge.materials.PlanType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Request for {@code POST /api/simulate/run} (CURRENT_PLAN_DESIGN §8.1).
 */
public record PlanRunRequest(
        List<String> tomeIds,
        PlanType plan,
        BigDecimal extraPayment,
        BigDecimal lumpSumPayment,
        Integer lumpSumMonth,
        LocalDate startMonth,
        Integer horizonMonths
) {
}
