package com.krath.utilisim.sim.oracle;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * DTO for analyzing and comparing multiple strategy outcomes.
 */
@Getter
@Setter
public class StrategyAnalysis {
    private List<StrategyComparison> comparisons;
    private String recommendedStrategy;
    private String analysisNotes;

    public StrategyAnalysis() {
    }

    public StrategyAnalysis(List<StrategyComparison> comparisons, String recommendedStrategy) {
        this.comparisons = comparisons;
        this.recommendedStrategy = recommendedStrategy;
    }
}

