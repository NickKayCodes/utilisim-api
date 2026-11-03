package com.krath.utilisim.sim.forge.anvil;

import com.krath.utilisim.sim.chronos.ChronosService;
import com.krath.utilisim.sim.forge.materials.PlanType;
import com.krath.utilisim.sim.forge.materials.TerminationReason;
import com.krath.utilisim.sim.ratio.UtilizationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * FORGE's amortization ledger (CURRENT_PLAN_DESIGN §3.4).
 *
 * <p>This is the single place where money is computed: interest accrual,
 * minimum/actual payment application, surplus allocation, lump sums and the
 * aggregate rows. ORACLE supplies {@code order}; RATIO converts balances to
 * utilization; CHRONOS supplies labels and period dates.</p>
 */
@Service
public class PayoffLedgerBuilder {

    private static final Logger log = LoggerFactory.getLogger(PayoffLedgerBuilder.class);

    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2);

    private final UtilizationService utilizationService;
    private final ChronosService chronosService;

    public PayoffLedgerBuilder(UtilizationService utilizationService, ChronosService chronosService) {
        this.utilizationService = utilizationService;
        this.chronosService = chronosService;
    }

    /** Per-run inputs. {@code startMonth} is only used for labels/dates. */
    public record Params(
            BigDecimal extraPayment,
            BigDecimal lumpSumPayment,
            int lumpSumMonth,
            int horizonMonths,
            LocalDate startMonth
    ) {
    }

    public record LedgerDebtMonth(
            String tomeId,
            String accountName,
            BigDecimal startingBalance,
            BigDecimal monthlyInterest,
            BigDecimal minimumDue,
            BigDecimal actualPayment,
            BigDecimal extraPayment,
            BigDecimal lumpSumApplied,
            BigDecimal remainingBalance,
            BigDecimal utilizationPct,
            BigDecimal interestPaid,
            BigDecimal amountToCoverInterest
    ) {
    }

    public record LedgerMonth(
            int monthIndex,
            String monthLabel,
            LocalDate periodStart,
            BigDecimal startingBalance,
            BigDecimal monthlyInterest,
            BigDecimal minimumDue,
            BigDecimal actualPayment,
            BigDecimal extraPayment,
            BigDecimal lumpSumApplied,
            BigDecimal remainingBalance,
            BigDecimal utilizationPct,
            BigDecimal interestPaid,
            BigDecimal amountToCoverInterest,
            List<LedgerDebtMonth> debts
    ) {
    }

    public record LedgerResult(List<LedgerMonth> months, TerminationReason reason) {
    }

    public LedgerResult build(
            List<DebtInput> debts,
            PlanType plan,
            List<DebtInput> order,
            Params params
    ) {
        if (debts == null || debts.isEmpty()) {
            return new LedgerResult(List.of(), TerminationReason.PAID_OFF);
        }

        int count = debts.size();
        BigDecimal[] balances = new BigDecimal[count];
        BigDecimal[] monthlyRate = new BigDecimal[count];
        BigDecimal[] minimum = new BigDecimal[count];
        BigDecimal[] currentPayment = new BigDecimal[count];
        BigDecimal[] limit = new BigDecimal[count];

        for (int i = 0; i < count; i++) {
            DebtInput debt = debts.get(i);
            balances[i] = money(debt.currentBalance());
            monthlyRate[i] = debt.monthlyRate();
            minimum[i] = money(debt.minimumPayment());
            currentPayment[i] = money(debt.currentPayment());
            limit[i] = debt.creditLimit();
        }

        boolean strategy = plan != null && plan.isStrategy();
        int lumpMonth = Math.max(1, params.lumpSumMonth());
        int horizon = Math.max(1, Math.min(params.horizonMonths(), 600));
        LocalDate startMonth = params.startMonth() == null
                ? LocalDate.now().withDayOfMonth(1)
                : params.startMonth();

        if (!anyPositive(balances)) {
            return new LedgerResult(List.of(), TerminationReason.PAID_OFF);
        }

        Map<String, Integer> indexByTome = new LinkedHashMap<>();
        for (int i = 0; i < count; i++) {
            indexByTome.put(debts.get(i).tomeId(), i);
        }
        List<Integer> targetOrder = new ArrayList<>();
        if (strategy && order != null) {
            for (DebtInput debt : order) {
                Integer index = indexByTome.get(debt.tomeId());
                if (index != null) {
                    targetOrder.add(index);
                }
            }
        }
        if (targetOrder.size() != count) {
            if (strategy) {
                log.warn("Incomplete debt order from ORACLE ({} of {} debts); using input order",
                        targetOrder.size(), count);
            }
            targetOrder.clear();
            for (int i = 0; i < count; i++) {
                targetOrder.add(i);
            }
        }

        // Budget is fixed at the start. A debt that is already closed does not
        // contribute a minimum; a debt that closes later frees its minimum for
        // the next target.
        BigDecimal budget = strategy ? nz(params.extraPayment()) : ZERO;
        for (int i = 0; i < count; i++) {
            if (balances[i].compareTo(ZERO) > 0) {
                budget = budget.add(minimum[i]);
            }
        }
        budget = money(budget);

        List<LedgerMonth> months = new ArrayList<>();
        TerminationReason reason = TerminationReason.HORIZON_REACHED;

        for (int month = 1; month <= horizon; month++) {
            BigDecimal[] start = new BigDecimal[count];
            BigDecimal[] interest = new BigDecimal[count];
            BigDecimal[] minDue = new BigDecimal[count];
            BigDecimal[] paid = new BigDecimal[count];
            BigDecimal[] lump = new BigDecimal[count];

            for (int i = 0; i < count; i++) {
                start[i] = balances[i];
                interest[i] = money(start[i].multiply(monthlyRate[i]));
                minDue[i] = start[i].add(interest[i]).min(minimum[i]);
                paid[i] = ZERO;
                lump[i] = ZERO;
            }

            // 1. contractual minimums
            for (int i = 0; i < count; i++) {
                if (start[i].compareTo(ZERO) > 0) {
                    paid[i] = minDue[i];
                }
            }

            // 2. plan payment
            if (plan == PlanType.CURRENT) {
                for (int i = 0; i < count; i++) {
                    paid[i] = start[i].add(interest[i]).min(currentPayment[i]);
                }
            } else if (strategy) {
                BigDecimal surplus = budget.subtract(sum(paid, count));
                for (int index : targetOrder) {
                    if (surplus.compareTo(ZERO) <= 0) {
                        break;
                    }
                    BigDecimal outstanding = start[index].add(interest[index]).subtract(paid[index]);
                    if (outstanding.compareTo(ZERO) <= 0) {
                        continue;
                    }
                    BigDecimal take = outstanding.min(surplus);
                    paid[index] = paid[index].add(take);
                    surplus = surplus.subtract(take);
                }
            }

            // 3. lump sum: strategy plans only (D1)
            if (strategy && month == lumpMonth) {
                BigDecimal rest = nz(params.lumpSumPayment());
                for (int index : targetOrder) {
                    if (rest.compareTo(ZERO) <= 0) {
                        break;
                    }
                    BigDecimal outstanding = start[index].add(interest[index]).subtract(paid[index]);
                    if (outstanding.compareTo(ZERO) <= 0) {
                        continue;
                    }
                    BigDecimal take = outstanding.min(rest);
                    paid[index] = paid[index].add(take);
                    lump[index] = lump[index].add(take);
                    rest = rest.subtract(take);
                }
            }

            // 4. settle
            List<LedgerDebtMonth> debtRows = new ArrayList<>(count);
            BigDecimal aggStart = ZERO;
            BigDecimal aggInterest = ZERO;
            BigDecimal aggMinDue = ZERO;
            BigDecimal aggActual = ZERO;
            BigDecimal aggExtra = ZERO;
            BigDecimal aggLump = ZERO;
            BigDecimal aggRemaining = ZERO;
            BigDecimal aggInterestPaid = ZERO;
            BigDecimal aggCover = ZERO;
            List<BigDecimal> remainingForUtilization = new ArrayList<>(count);
            List<BigDecimal> limitsForUtilization = new ArrayList<>(count);
            boolean anyReduced = false;

            for (int i = 0; i < count; i++) {
                BigDecimal remaining = start[i].add(interest[i]).subtract(paid[i]).max(ZERO);
                BigDecimal actualPayment = paid[i].subtract(lump[i]).max(ZERO);
                BigDecimal extraPayment = paid[i].subtract(minDue[i]).subtract(lump[i]).max(ZERO);
                BigDecimal interestPaid = interest[i].min(paid[i]);
                BigDecimal utilization = utilizationService.percent(remaining, limit[i]);

                balances[i] = remaining;
                if (remaining.compareTo(start[i]) < 0) {
                    anyReduced = true;
                }

                debtRows.add(new LedgerDebtMonth(
                        debts.get(i).tomeId(),
                        debts.get(i).accountName(),
                        start[i],
                        interest[i],
                        minDue[i],
                        actualPayment,
                        extraPayment,
                        lump[i],
                        remaining,
                        utilization,
                        interestPaid,
                        interest[i]
                ));

                aggStart = aggStart.add(start[i]);
                aggInterest = aggInterest.add(interest[i]);
                aggMinDue = aggMinDue.add(minDue[i]);
                aggActual = aggActual.add(actualPayment);
                aggExtra = aggExtra.add(extraPayment);
                aggLump = aggLump.add(lump[i]);
                aggRemaining = aggRemaining.add(remaining);
                aggInterestPaid = aggInterestPaid.add(interestPaid);
                aggCover = aggCover.add(interest[i]);
                remainingForUtilization.add(remaining);
                limitsForUtilization.add(limit[i]);
            }

            LocalDate periodStart = chronosService.monthStart(startMonth, month);
            months.add(new LedgerMonth(
                    month,
                    chronosService.label(periodStart),
                    periodStart,
                    money(aggStart),
                    money(aggInterest),
                    money(aggMinDue),
                    money(aggActual),
                    money(aggExtra),
                    money(aggLump),
                    money(aggRemaining),
                    utilizationService.aggregatePercent(remainingForUtilization, limitsForUtilization),
                    money(aggInterestPaid),
                    money(aggCover),
                    List.copyOf(debtRows)
            ));

            if (aggRemaining.compareTo(ZERO) <= 0) {
                reason = TerminationReason.PAID_OFF;
                break;
            }
            if (!anyReduced) {
                reason = TerminationReason.STAGNANT;
                break;
            }
        }

        return new LedgerResult(List.copyOf(months), reason);
    }

    private static BigDecimal nz(BigDecimal value) {
        return value == null ? ZERO : money(value);
    }

    private static BigDecimal money(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    private static BigDecimal sum(BigDecimal[] values, int count) {
        BigDecimal total = ZERO;
        for (int i = 0; i < count; i++) {
            total = total.add(values[i]);
        }
        return total;
    }

    private static boolean anyPositive(BigDecimal[] balances) {
        for (BigDecimal balance : balances) {
            if (balance.compareTo(ZERO) > 0) {
                return true;
            }
        }
        return false;
    }
}
