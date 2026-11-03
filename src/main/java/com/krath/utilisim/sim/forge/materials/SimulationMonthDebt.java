package com.krath.utilisim.sim.forge.materials;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/** One debt's row for one month of a persisted plan (CURRENT_PLAN_DESIGN §6.4). */
@Entity
@Table(name = "simulation_month_debt",
        uniqueConstraints = @UniqueConstraint(columnNames = {"run_id", "month_index", "tome_id"}))
@Getter
@Setter
public class SimulationMonthDebt {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long runId;
    private int monthIndex;
    private String tomeId;
    private String accountName;

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
