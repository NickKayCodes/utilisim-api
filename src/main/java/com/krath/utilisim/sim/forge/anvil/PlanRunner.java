package com.krath.utilisim.sim.forge.anvil;

import com.krath.utilisim.sim.forge.hammer.DebtMonthLedger;
import com.krath.utilisim.sim.forge.hammer.MonthLedger;
import com.krath.utilisim.sim.forge.hammer.PlanTimeline;
import com.krath.utilisim.sim.forge.materials.PlanType;
import com.krath.utilisim.sim.forge.materials.TerminationReason;
import com.krath.utilisim.sim.oracle.StrategyService;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Runs one plan over an already-loaded debt set and maps the ledger to the API
 * shape (CURRENT_PLAN_DESIGN §10.1).
 */
@Service
public class PlanRunner {

    private final StrategyService strategyService;
    private final PayoffLedgerBuilder ledgerBuilder;

    public PlanRunner(StrategyService strategyService, PayoffLedgerBuilder ledgerBuilder) {
        this.strategyService = strategyService;
        this.ledgerBuilder = ledgerBuilder;
    }

    public PlanTimeline run(
            List<DebtInput> debts,
            PlanType plan,
            long runId,
            PayoffLedgerBuilder.Params params
    ) {
        List<DebtInput> order = plan.isStrategy()
                ? strategyService.orderFor(debts, plan)
                : debts;

        PayoffLedgerBuilder.LedgerResult ledger = ledgerBuilder.build(debts, plan, order, params);

        List<MonthLedger> months = new ArrayList<>(ledger.months().size());
        BigDecimal totalInterest = BigDecimal.ZERO;
        BigDecimal totalPaid = BigDecimal.ZERO;
        BigDecimal peakUtilization = null;

        for (PayoffLedgerBuilder.LedgerMonth month : ledger.months()) {
            totalInterest = totalInterest.add(month.monthlyInterest());
            totalPaid = totalPaid.add(month.actualPayment()).add(month.lumpSumApplied());
            if (month.utilizationPct() != null
                    && (peakUtilization == null || month.utilizationPct().compareTo(peakUtilization) > 0)) {
                peakUtilization = month.utilizationPct();
            }
            months.add(toDto(month));
        }

        BigDecimal finalBalance = months.isEmpty()
                ? sumBalances(debts)
                : months.get(months.size() - 1).remainingBalance();

        boolean paidOff = ledger.reason() == TerminationReason.PAID_OFF;
        LocalDate payoffDate = paidOff && !months.isEmpty()
                ? months.get(months.size() - 1).periodStart()
                : null;

        return new PlanTimeline(
                runId,
                plan,
                plan.name(),
                ledger.reason().toStatus(),
                ledger.reason(),
                paidOff ? months.size() : 0,
                payoffDate,
                totalInterest.setScale(2, java.math.RoundingMode.HALF_UP),
                totalPaid.setScale(2, java.math.RoundingMode.HALF_UP),
                finalBalance,
                peakUtilization,
                List.copyOf(months)
        );
    }

    private static MonthLedger toDto(PayoffLedgerBuilder.LedgerMonth month) {
        List<DebtMonthLedger> debts = month.debts().stream()
                .map(debt -> new DebtMonthLedger(
                        debt.tomeId(),
                        debt.accountName(),
                        debt.startingBalance(),
                        debt.monthlyInterest(),
                        debt.minimumDue(),
                        debt.actualPayment(),
                        debt.extraPayment(),
                        debt.lumpSumApplied(),
                        debt.remainingBalance(),
                        debt.utilizationPct(),
                        debt.interestPaid(),
                        debt.amountToCoverInterest()))
                .toList();

        return new MonthLedger(
                month.monthIndex(),
                month.monthLabel(),
                month.periodStart(),
                month.startingBalance(),
                month.monthlyInterest(),
                month.minimumDue(),
                month.actualPayment(),
                month.extraPayment(),
                month.lumpSumApplied(),
                month.remainingBalance(),
                month.utilizationPct(),
                month.interestPaid(),
                month.amountToCoverInterest(),
                debts);
    }

    private static BigDecimal sumBalances(List<DebtInput> debts) {
        BigDecimal total = BigDecimal.ZERO;
        for (DebtInput debt : debts) {
            total = total.add(debt.currentBalance());
        }
        return total.setScale(2, java.math.RoundingMode.HALF_UP);
    }
}
