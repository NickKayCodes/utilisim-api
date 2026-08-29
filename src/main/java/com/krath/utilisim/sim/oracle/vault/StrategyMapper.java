package com.krath.utilisim.sim.oracle.vault;

import com.krath.utilisim.sim.oracle.DebtSnapshot;
import com.krath.utilisim.sim.oracle.StrategyInput;
import com.krath.utilisim.sim.forge.materials.StrategyType;
import com.krath.utilisim.ledger.tome.Tome;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Strategy Mapper - Converts between Tome entities, StrategyConfig entities, and DTOs.
 * Handles JSON serialization of custom debt orders.
 */
@Component
public class StrategyMapper {

    private final ObjectMapper objectMapper;

    public StrategyMapper() {
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Convert a Tome entity to a DebtSnapshot DTO.
     */
    public DebtSnapshot toDebtSnapshot(Tome tome) {
        return new DebtSnapshot(
            tome.getId(),
            tome.getName(),
            tome.getCurrentBalance(),
            tome.getApr(),
            tome.getEstimatedApr(),
            tome.getMinimumDebtPayment(),
            tome.getApr() > 0 ? tome.getApr() : tome.getEstimatedApr()
        );
    }

    /**
     * Convert a list of Tome entities to DebtSnapshot DTOs.
     */
    public List<DebtSnapshot> toDebtSnapshots(List<Tome> tomes) {
        List<DebtSnapshot> snapshots = new ArrayList<>();
        for (Tome tome : tomes) {
            snapshots.add(toDebtSnapshot(tome));
        }
        return snapshots;
    }

    /**
     * Convert StrategyConfig entity to StrategyInput DTO.
     */
    public StrategyInput toStrategyInput(StrategyConfig config) {
        List<String> customOrder = parseCustomDebtOrder(config.getCustomDebtOrder());
        return new StrategyInput(
            config.getStrategyType(),
            customOrder,
            config.getExtraPayment(),
            config.getLumpSumPayment()
        );
    }

    /**
     * Convert StrategyInput DTO to StrategyConfig entity.
     */
    public StrategyConfig toStrategyConfig(
            StrategyInput input,
            String userId,
            String scenarioId,
            String description
    ) {
        StrategyConfig config = new StrategyConfig();
        config.setUserId(userId);
        config.setScenarioId(scenarioId);
        config.setStrategyType(input.strategyType());
        config.setCustomDebtOrder(serializeCustomDebtOrder(input.customDebtOrder()));
        config.setExtraPayment(input.extraPayment());
        config.setLumpSumPayment(input.lumpSumPayment());
        config.setDescription(description);
        config.setCreatedAt(OffsetDateTime.now());
        config.setUpdatedAt(OffsetDateTime.now());
        return config;
    }

    /**
     * Serialize custom debt order list to JSON string.
     */
    private String serializeCustomDebtOrder(List<String> customOrder) {
        if (customOrder == null || customOrder.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(customOrder);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Deserialize custom debt order JSON string to list.
     */
    private List<String> parseCustomDebtOrder(String json) {
        if (json == null || json.trim().isEmpty()) {
            return new ArrayList<>();
        }
        try {
            return Arrays.asList(objectMapper.readValue(json, String[].class));
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }
}

