package com.krath.utilisim.sim.oracle.vault;

import com.krath.utilisim.sim.forge.materials.StrategyType;
import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * JPA Entity for persisting strategy configurations.
 * Linked to ScenarioContext to define the strategy for debt repayment.
 */
@Entity
@Table(name = "strategy_config")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
public class StrategyConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    private String userId;  // FK to AppUser
    private String scenarioId;  // FK to ScenarioContext

    @Enumerated(EnumType.STRING)
    private StrategyType strategyType;

    @Column(columnDefinition = "TEXT")
    private String customDebtOrder;  // JSON array of tome IDs for CUSTOM strategy

    private float extraPayment;
    private float lumpSumPayment;

    @Column(columnDefinition = "TEXT")
    private String description;  // Strategy explanation/rationale

    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}

