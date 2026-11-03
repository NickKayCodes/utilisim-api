package com.krath.utilisim.sim.forge.materials;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

/** One aggregate month of a persisted plan (CURRENT_PLAN_DESIGN §6.3). */
@Entity
@Table(name = "simulation_month",
        uniqueConstraints = @UniqueConstraint(columnNames = {"run_id", "month_index"}))
@Getter
@Setter
public class SimulationMonth {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long runId;
    private int monthIndex;
    private String monthLabel;
    private LocalDate periodStart;

    private BigDecimal startingBalance;
    private BigDecimal monthlyInterest;
    private BigDecimal minimumDue;
    private BigDecimal actualPayment;
    private BigDecimal extraPayment;
    private BigDecimal lumpSumApplied;
    private BigDecimal remainingBalance;
    private BigDecimal utilizationPct;
    private BigDecimal interestPaid;
    private BigDecimal amountToCoverInterest;
}
