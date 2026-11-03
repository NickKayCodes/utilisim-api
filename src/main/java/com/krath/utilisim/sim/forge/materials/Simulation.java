package com.krath.utilisim.sim.forge.materials;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "simulation_run")
@Getter
@Setter
public class Simulation {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String userId;

    @Enumerated(EnumType.STRING)
    private SimulationType type;

    @Enumerated(EnumType.STRING)
    private SimulationStatus status;

    @Enumerated(EnumType.STRING)
    private PlanType planType;

    @Enumerated(EnumType.STRING)
    private TerminationReason terminationReason;

    private UUID comparisonGroupId;

    private LocalDate startMonth;
    private Integer horizonMonths;

    private BigDecimal extraPayment;
    private BigDecimal lumpSumPayment;
    private Integer lumpSumMonth;

    private BigDecimal totalInterest;
    private BigDecimal totalPaid;
    private BigDecimal finalBalance;
    private Integer monthsToPayoff;
    private OffsetDateTime payoffDate;
    private BigDecimal peakUtilization;

    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
