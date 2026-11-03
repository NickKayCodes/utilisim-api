package com.krath.utilisim.sim.oracle;

import com.krath.utilisim.sim.forge.hammer.PlanTimeline;
import com.krath.utilisim.sim.forge.materials.PlanType;
import com.krath.utilisim.sim.forge.materials.TerminationReason;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;

/**
 * ORACLE's recommendation layer (CURRENT_PLAN_DESIGN §3.2, R8).
 *
 * <p>It ranks already-computed {@link PlanTimeline}s. It never accrues
 * interest, applies a payment, or re-runs a plan.</p>
 */
@Service
public class RecommendationService {

    public record Recommendation(PlanType plan, String basis) {
    }

    /**
     * Deterministic ranking: repaid plans first, then lowest total interest,
     * then the shorter payoff, then the plan name.
     *
     * <p>A plan that did not clear the debt cannot be recommended, so the
     * result is {@code null} when nothing was repaid.</p>
     */
    public Recommendation recommend(List<PlanTimeline> timelines) {
        if (timelines == null || timelines.isEmpty()) {
            return new Recommendation(null, "no plans were computed");
        }

        return timelines.stream()
                .filter(timeline -> timeline.terminationReason() == TerminationReason.PAID_OFF)
                .min(Comparator
                        .comparing(PlanTimeline::totalInterest)
                        .thenComparingInt(PlanTimeline::monthsToPayoff)
                        .thenComparing(timeline -> timeline.plan().name()))
                .map(timeline -> new Recommendation(
                        timeline.plan(),
                        "lowest total interest among plans that paid the debt off"))
                .orElseGet(() -> new Recommendation(
                        null,
                        "no plan paid the debt off within the horizon"));
    }
}
