package com.krath.utilisim.sim.oracle.vault;

import com.krath.utilisim.sim.oracle.StrategyInput;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Strategy Configuration Service - Manages persistence of strategy configurations.
 * Handles CRUD operations for StrategyConfig entities.
 */
@Service
public class StrategyConfigService {

    @Autowired
    private StrategyConfigRepository strategyConfigRepository;

    @Autowired
    private StrategyMapper strategyMapper;

    /**
     * Create and persist a new strategy configuration.
     */
    public StrategyConfig createStrategyConfig(
            StrategyInput input,
            String userId,
            String scenarioId,
            String description
    ) {
        StrategyConfig config = strategyMapper.toStrategyConfig(input, userId, scenarioId, description);
        return strategyConfigRepository.save(config);
    }

    /**
     * Retrieve strategy configuration by scenario ID.
     */
    public Optional<StrategyConfig> getByScenarioId(String scenarioId) {
        return strategyConfigRepository.findByScenarioId(scenarioId);
    }

    /**
     * Retrieve all strategy configurations for a user.
     */
    public List<StrategyConfig> getUserConfigurations(String userId) {
        return strategyConfigRepository.findByUserId(userId);
    }

    /**
     * Update an existing strategy configuration.
     */
    public StrategyConfig updateStrategyConfig(
            String configId,
            StrategyInput input,
            String description
    ) {
        Optional<StrategyConfig> existing = strategyConfigRepository.findById(configId);

        if (existing.isPresent()) {
            StrategyConfig config = existing.get();
            config.setStrategyType(input.strategyType());
            config.setCustomDebtOrder(strategyMapper.toStrategyInput(config).customDebtOrder() != null
                ? input.customDebtOrder().toString() : null);
            config.setExtraPayment(input.extraPayment());
            config.setLumpSumPayment(input.lumpSumPayment());
            config.setDescription(description);
            config.setUpdatedAt(OffsetDateTime.now());

            return strategyConfigRepository.save(config);
        }

        return null;
    }

    /**
     * Delete a strategy configuration.
     */
    public void deleteStrategyConfig(String configId) {
        strategyConfigRepository.deleteById(configId);
    }

    /**
     * Retrieve a specific strategy configuration by ID.
     */
    public Optional<StrategyConfig> getById(String configId) {
        return strategyConfigRepository.findById(configId);
    }
}

