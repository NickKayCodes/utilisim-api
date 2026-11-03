package com.krath.utilisim.sim.forge.controller;

import com.krath.utilisim.sentinel.foundation.AppUser;
import com.krath.utilisim.sim.forge.anvil.PlanComparisonService;
import com.krath.utilisim.sim.forge.hammer.ComparisonRequest;
import com.krath.utilisim.sim.forge.hammer.ComparisonResponse;
import com.krath.utilisim.sim.forge.hammer.PlanRunRequest;
import com.krath.utilisim.sim.forge.hammer.PlanTimeline;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Simulation ledger API (CURRENT_PLAN_DESIGN §9).
 *
 * <p>The timeline is the product output; the recommendation is a derived field
 * on the comparison response.</p>
 */
@RestController
@RequestMapping("/api/simulate")
public class SimulationController {

    private final PlanComparisonService planComparisonService;

    public SimulationController(PlanComparisonService planComparisonService) {
        this.planComparisonService = planComparisonService;
    }

    @PostMapping("/compare")
    public ComparisonResponse compare(
            @AuthenticationPrincipal AppUser user,
            @RequestBody ComparisonRequest request
    ) {
        return planComparisonService.compare(request, user.getId());
    }

    @PostMapping("/run")
    public PlanTimeline run(
            @AuthenticationPrincipal AppUser user,
            @RequestBody PlanRunRequest request
    ) {
        return planComparisonService.run(request, user.getId());
    }

    @GetMapping("/runs/{runId}")
    public PlanTimeline run(
            @AuthenticationPrincipal AppUser user,
            @PathVariable Long runId
    ) {
        return planComparisonService.getRun(runId, user.getId());
    }

    @GetMapping("/runs")
    public List<PlanTimeline> history(
            @AuthenticationPrincipal AppUser user,
            @RequestParam(defaultValue = "20") int limit
    ) {
        return planComparisonService.history(user.getId(), limit);
    }

    @GetMapping("/comparisons/{comparisonGroupId}")
    public ComparisonResponse comparison(
            @AuthenticationPrincipal AppUser user,
            @PathVariable UUID comparisonGroupId
    ) {
        return planComparisonService.getComparison(comparisonGroupId, user.getId());
    }
}
