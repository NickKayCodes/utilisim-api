package com.krath.utilisim.sim.oracle;

import com.krath.utilisim.sim.forge.materials.StrategyType;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Strategy Service for debt repayment ordering.
 * Implements Snowball, Avalanche, Hybrid, and Custom ordering strategies.
 *
 * SNOWBALL: Pay off smallest balance first (psychological wins)
 * AVALANCHE: Pay off highest interest rate first (mathematical optimal)
 * HYBRID: Combination - prioritize high interest but grouped by interest bands
 * CUSTOM: User-defined ordering by debt priorities
 */
@Service
public class StrategyService {

    /**
     * Order debts based on the specified strategy.
     *
     * @param debts The list of debts to order
     * @param input Strategy input configuration
     * @return StrategyOutput containing ordered debts
     */
    public StrategyOutput orderDebts(List<DebtSnapshot> debts, StrategyInput input) {

        if (debts == null || debts.isEmpty()) {
            return new StrategyOutput(
                input.strategyType().name(),
                Collections.emptyList(),
                "No debts to order"
            );
        }

        List<DebtSnapshot> orderedDebts;
        String description;

        switch (input.strategyType()) {
            case SNOWBALL:
                orderedDebts = orderBySnowball(debts);
                description = "Snowball: Pay off smallest balance first for psychological momentum";
                break;
            case AVALANCHE:
                orderedDebts = orderByAvalanche(debts);
                description = "Avalanche: Pay off highest interest rate first for mathematical optimization";
                break;
            case HYBRID:
                orderedDebts = orderByHybrid(debts);
                description = "Hybrid: Balance between interest rate and balance amount";
                break;
            case CUSTOM:
                orderedDebts = orderByCustom(debts, input.customDebtOrder());
                description = "Custom: User-defined ordering by debt priorities";
                break;
            case MINIMUM_ONLY:
            default:
                orderedDebts = new ArrayList<>(debts);
                description = "Minimum only: No strategic ordering applied";
                break;
        }

        return new StrategyOutput(
            input.strategyType().name(),
            orderedDebts,
            description
        );
    }

    /**
     * SNOWBALL Strategy: Order by balance (smallest to largest).
     * Psychological motivation from paying off accounts completely.
     */
    private List<DebtSnapshot> orderBySnowball(List<DebtSnapshot> debts) {
        return debts.stream()
            .sorted(Comparator.comparingDouble(DebtSnapshot::currentBalance))
            .toList();
    }

    /**
     * AVALANCHE Strategy: Order by interest rate (highest to lowest).
     * Mathematically optimal: save the most interest.
     */
    private List<DebtSnapshot> orderByAvalanche(List<DebtSnapshot> debts) {
        return debts.stream()
            .sorted((d1, d2) -> {
                float apr1 = d1.apr() > 0 ? d1.apr() : d1.estimatedApr();
                float apr2 = d2.apr() > 0 ? d2.apr() : d2.estimatedApr();
                return Float.compare(apr2, apr1);  // Descending order
            })
            .toList();
    }

    /**
     * HYBRID Strategy: Balance both interest rate and balance amount.
     * Groups debts by interest rate bands and applies snowball within each band.
     * High-interest debts are prioritized, but psychological wins matter.
     */
    private List<DebtSnapshot> orderByHybrid(List<DebtSnapshot> debts) {
        // Calculate interest bands
        List<DebtSnapshot> sorted = new ArrayList<>(debts);

        // First, sort by interest rate (descending)
        sorted.sort((d1, d2) -> {
            float apr1 = d1.apr() > 0 ? d1.apr() : d1.estimatedApr();
            float apr2 = d2.apr() > 0 ? d2.apr() : d2.estimatedApr();
            return Float.compare(apr2, apr1);
        });

        // Within each interest band (5% range), apply snowball ordering
        List<DebtSnapshot> result = new ArrayList<>();
        List<DebtSnapshot> currentBand = new ArrayList<>();
        float currentBandRate = -1;

        for (DebtSnapshot debt : sorted) {
            float apr = debt.apr() > 0 ? debt.apr() : debt.estimatedApr();

            // If in different band, sort current band by balance and add to result
            if (Math.abs(apr - currentBandRate) > 0.05f && !currentBand.isEmpty()) {
                currentBand.sort(Comparator.comparingDouble(DebtSnapshot::currentBalance));
                result.addAll(currentBand);
                currentBand.clear();
            }

            currentBandRate = apr;
            currentBand.add(debt);
        }

        // Add the last band
        if (!currentBand.isEmpty()) {
            currentBand.sort(Comparator.comparingDouble(DebtSnapshot::currentBalance));
            result.addAll(currentBand);
        }

        return result;
    }

    /**
     * CUSTOM Strategy: User-defined ordering by debt priorities.
     * Respects the provided customDebtOrder list of tome IDs.
     * Unmapped debts are appended at the end.
     */
    private List<DebtSnapshot> orderByCustom(List<DebtSnapshot> debts, List<String> customOrder) {
        if (customOrder == null || customOrder.isEmpty()) {
            return new ArrayList<>(debts);
        }

        Map<String, DebtSnapshot> debtMap = new HashMap<>();
        for (DebtSnapshot debt : debts) {
            debtMap.put(debt.tomeId(), debt);
        }

        List<DebtSnapshot> result = new ArrayList<>();
        Set<String> added = new HashSet<>();

        // Add debts in custom order
        for (String tomeId : customOrder) {
            DebtSnapshot debt = debtMap.get(tomeId);
            if (debt != null) {
                result.add(debt);
                added.add(tomeId);
            }
        }

        // Append any debts not in custom order
        for (DebtSnapshot debt : debts) {
            if (!added.contains(debt.tomeId())) {
                result.add(debt);
            }
        }

        return result;
    }

    /**
     * Calculate the total interest for a given ordering strategy.
     * This is useful for comparing strategies.
     *
     * @param orderedDebts The debts ordered by strategy
     * @return Total interest across all debts (simple calculation)
     */
    public float calculateTotalInterestForOrdering(List<DebtSnapshot> orderedDebts) {
        return orderedDebts.stream()
            .map(debt -> debt.currentBalance() * (debt.apr() > 0 ? debt.apr() : debt.estimatedApr()))
            .reduce(0f, Float::sum);
    }

    /**
     * Validate strategy ordering is deterministic for the given input.
     */
    public boolean isOrderingDeterministic(StrategyType strategyType) {
        // SNOWBALL, AVALANCHE, and HYBRID are deterministic based on account properties
        // CUSTOM is deterministic if customDebtOrder is defined
        return strategyType != null && !strategyType.equals(StrategyType.MINIMUM_ONLY);
    }
}

