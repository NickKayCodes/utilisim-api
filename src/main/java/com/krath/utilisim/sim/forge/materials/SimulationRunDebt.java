package com.krath.utilisim.sim.forge.materials;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * Immutable input snapshot for one debt in one run (CURRENT_PLAN_DESIGN §6.5).
 * This is what makes a stored comparison reproducible after the account changes.
 */
@Entity
@Table(name = "simulation_run_debt",
        uniqueConstraints = @UniqueConstraint(columnNames = {"run_id", "tome_id"}))
@Getter
@Setter
public class SimulationRunDebt {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long runId;
    private String tomeId;
    private String accountName;

    private BigDecimal startingBalance;
    private BigDecimal apr;
    private BigDecimal minimumPayment;
    private BigDecimal currentPayment;
    private BigDecimal creditLimit;
}
