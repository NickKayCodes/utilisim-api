package com.krath.utilisim.sim.oracle;

import com.krath.utilisim.sim.forge.hammer.PlanTimeline;
import com.krath.utilisim.sim.forge.materials.PlanType;
import com.krath.utilisim.sim.forge.materials.SimulationStatus;
import com.krath.utilisim.sim.forge.materials.TerminationReason;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RecommendationServiceTest {

    private final RecommendationService service = new RecommendationService();

    private PlanTimeline timeline(
            PlanType plan,
            String interest,
            int months,
            TerminationReason reason
    ) {
        return new PlanTimeline(
                1L,
                plan,
                plan.name(),
                reason.toStatus(),
                reason,
                months,
                null,
                new BigDecimal(interest),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                List.of());
    }

    @Test
    @DisplayName("the cheapest repaid plan wins")
    void recommendsCheapestRepaidPlan() {
        var result = service.recommend(List.of(
                timeline(PlanType.SNOWBALL, "150.00", 20, TerminationReason.PAID_OFF),
                timeline(PlanType.AVALANCHE, "100.00", 22, TerminationReason.PAID_OFF),
                timeline(PlanType.HYBRID, "120.00", 21, TerminationReason.PAID_OFF)));

        assertEquals(PlanType.AVALANCHE, result.plan());
        assertTrue(result.basis().contains("lowest total interest"));
    }

    @Test
    @DisplayName("a plan that did not repay cannot win, even with lower interest")
    void ignoresUnpaidPlans() {
        var result = service.recommend(List.of(
                timeline(PlanType.AVALANCHE, "50.00", 0, TerminationReason.HORIZON_REACHED),
                timeline(PlanType.SNOWBALL, "200.00", 24, TerminationReason.PAID_OFF)));

        assertEquals(PlanType.SNOWBALL, result.plan());
    }

    @Test
    @DisplayName("ties break on months, then plan name")
    void breaksTiesDeterministically() {
        var result = service.recommend(List.of(
                timeline(PlanType.SNOWBALL, "100.00", 24, TerminationReason.PAID_OFF),
                timeline(PlanType.AVALANCHE, "100.00", 20, TerminationReason.PAID_OFF),
                timeline(PlanType.HYBRID, "100.00", 20, TerminationReason.PAID_OFF)));

        assertEquals(PlanType.AVALANCHE, result.plan());
    }

    @Test
    @DisplayName("no recommendation when nothing was repaid")
    void returnsNullWhenNothingRepaid() {
        var result = service.recommend(List.of(
                timeline(PlanType.CURRENT, "50.00", 0, TerminationReason.STAGNANT),
                timeline(PlanType.MINIMUM, "70.00", 0, TerminationReason.HORIZON_REACHED)));

        assertNull(result.plan());
        assertTrue(result.basis().contains("no plan"));
    }

    @Test
    @DisplayName("an empty list has no recommendation")
    void handlesEmptyList() {
        assertNull(service.recommend(List.of()).plan());
        assertEquals(SimulationStatus.RUNNING, TerminationReason.NONE.toStatus());
    }
}
