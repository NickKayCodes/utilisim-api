package com.krath.utilisim.sim.oracle.vault;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for StrategyConfig persistence.
 */
public interface StrategyConfigRepository extends JpaRepository<StrategyConfig, String> {
    List<StrategyConfig> findByUserId(String userId);
    Optional<StrategyConfig> findByScenarioId(String scenarioId);
    List<StrategyConfig> findByUserIdAndScenarioId(String userId, String scenarioId);
}

