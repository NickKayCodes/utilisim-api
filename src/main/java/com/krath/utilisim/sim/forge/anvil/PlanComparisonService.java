package com.krath.utilisim.sim.forge.anvil;

import com.krath.utilisim.ledger.scribe.Scribe;
import com.krath.utilisim.ledger.tome.Tome;
import com.krath.utilisim.sim.forge.hammer.ComparisonRequest;
import com.krath.utilisim.sim.forge.hammer.ComparisonResponse;
import com.krath.utilisim.sim.forge.hammer.DebtMonthLedger;
import com.krath.utilisim.sim.forge.hammer.MonthLedger;
import com.krath.utilisim.sim.forge.hammer.PlanRunRequest;
import com.krath.utilisim.sim.forge.hammer.PlanTimeline;
import com.krath.utilisim.sim.forge.materials.PlanType;
import com.krath.utilisim.sim.forge.materials.Simulation;
import com.krath.utilisim.sim.forge.materials.SimulationMonth;
import com.krath.utilisim.sim.forge.materials.SimulationMonthDebt;
import com.krath.utilisim.sim.forge.materials.SimulationRunDebt;
import com.krath.utilisim.sim.forge.materials.SimulationStatus;
import com.krath.utilisim.sim.forge.materials.SimulationType;
import com.krath.utilisim.sim.forge.materials.TerminationReason;
import com.krath.utilisim.sim.forge.vault.SimulationMonthDebtRepository;
import com.krath.utilisim.sim.forge.vault.SimulationMonthRepository;
import com.krath.utilisim.sim.forge.vault.SimulationRepository;
import com.krath.utilisim.sim.forge.vault.SimulationRunDebtRepository;
import com.krath.utilisim.sim.oracle.RecommendationService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;

/**
 * FORGE's comparison orchestration (CURRENT_PLAN_DESIGN §10.3).
 *
 * <p>Loads the caller's debts, snapshots the inputs, runs each requested plan
 * through {@link PlanRunner}, persists the rows, and asks ORACLE to rank the
 * finished timelines.</p>
 */
@Service
public class PlanComparisonService {

    private static final int DEFAULT_HORIZON = 120;
    private static final int MAX_HORIZON = 600;

    private final Scribe scribe;
    private final PlanRunner planRunner;
    private final RecommendationService recommendationService;
    private final SimulationRepository simulationRepository;
    private final SimulationMonthRepository monthRepository;
    private final SimulationMonthDebtRepository monthDebtRepository;
    private final SimulationRunDebtRepository runDebtRepository;

    public PlanComparisonService(
            Scribe scribe,
            PlanRunner planRunner,
            RecommendationService recommendationService,
            SimulationRepository simulationRepository,
            SimulationMonthRepository monthRepository,
            SimulationMonthDebtRepository monthDebtRepository,
            SimulationRunDebtRepository runDebtRepository
    ) {
        this.scribe = scribe;
        this.planRunner = planRunner;
        this.recommendationService = recommendationService;
        this.simulationRepository = simulationRepository;
        this.monthRepository = monthRepository;
        this.monthDebtRepository = monthDebtRepository;
        this.runDebtRepository = runDebtRepository;
    }

    @Transactional
    public ComparisonResponse compare(ComparisonRequest request, String userId) {
        List<DebtInput> debts = loadDebts(request.tomeIds(), userId);
        if (debts.isEmpty()) {
            throw new IllegalArgumentException("Select at least one account to simulate");
        }

        int horizon = horizon(request.horizonMonths());
        int lumpMonth = lumpSumMonth(request.lumpSumMonth(), horizon);
        BigDecimal extra = nonNegative(request.extraPayment(), "extraPayment");
        BigDecimal lumpSum = nonNegative(request.lumpSumPayment(), "lumpSumPayment");
        LocalDate startMonth = startMonth(request.startMonth());
        List<PlanType> plans = request.plans() == null || request.plans().isEmpty()
                ? List.of(PlanType.values())
                : request.plans();
        // Duplicate plan entries would persist the same plan twice in one group.
        plans = new ArrayList<>(new LinkedHashSet<>(plans));

        UUID group = UUID.randomUUID();
        PayoffLedgerBuilder.Params params =
                new PayoffLedgerBuilder.Params(extra, lumpSum, lumpMonth, horizon, startMonth);

        List<PlanTimeline> timelines = new ArrayList<>();
        for (PlanType plan : plans) {
            timelines.add(runAndPersist(debts, plan, group, params, userId));
        }

        RecommendationService.Recommendation recommendation =
                recommendationService.recommend(timelines);

        return new ComparisonResponse(
                group,
                startMonth,
                horizon,
                totalBalance(debts),
                recommendation.plan(),
                recommendation.basis(),
                List.copyOf(timelines));
    }

    @Transactional
    public PlanTimeline run(PlanRunRequest request, String userId) {
        List<DebtInput> debts = loadDebts(request.tomeIds(), userId);
        if (debts.isEmpty()) {
            throw new IllegalArgumentException("Select at least one account to simulate");
        }

        int horizon = horizon(request.horizonMonths());
        int lumpMonth = lumpSumMonth(request.lumpSumMonth(), horizon);
        PlanType plan = request.plan() == null ? PlanType.CURRENT : request.plan();

        PayoffLedgerBuilder.Params params = new PayoffLedgerBuilder.Params(
                nonNegative(request.extraPayment(), "extraPayment"),
                nonNegative(request.lumpSumPayment(), "lumpSumPayment"),
                lumpMonth,
                horizon,
                startMonth(request.startMonth()));

        return runAndPersist(debts, plan, UUID.randomUUID(), params, userId);
    }

    @Transactional(readOnly = true)
    public PlanTimeline getRun(long runId, String userId) {
        Simulation run = simulationRepository.findByIdAndUserId(runId, userId)
                .orElseThrow(() -> new NoSuchElementException("Simulation run not found"));
        return toTimeline(run);
    }

    @Transactional(readOnly = true)
    public ComparisonResponse getComparison(UUID groupId, String userId) {
        List<Simulation> runs = simulationRepository
                .findByComparisonGroupIdOrderByIdAsc(groupId)
                .stream()
                .filter(run -> userId.equals(run.getUserId()))
                .toList();

        if (runs.isEmpty()) {
            throw new NoSuchElementException("Comparison not found");
        }

        List<PlanTimeline> timelines = runs.stream().map(this::toTimeline).toList();
        RecommendationService.Recommendation recommendation =
                recommendationService.recommend(timelines);

        Simulation first = runs.get(0);
        return new ComparisonResponse(
                groupId,
                first.getStartMonth(),
                first.getHorizonMonths() == null ? DEFAULT_HORIZON : first.getHorizonMonths(),
                totalBalanceFromSnapshot(first.getId()),
                recommendation.plan(),
                recommendation.basis(),
                timelines);
    }

    @Transactional(readOnly = true)
    public List<PlanTimeline> history(String userId, int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 100));
        return simulationRepository.findByUserIdOrderByIdDesc(userId).stream()
                .limit(safeLimit)
                .map(this::toTimeline)
                .toList();
    }

    // ------------------------------------------------------------------ run

    private PlanTimeline runAndPersist(
            List<DebtInput> debts,
            PlanType plan,
            UUID group,
            PayoffLedgerBuilder.Params params,
            String userId
    ) {
        Simulation run = new Simulation();
        run.setUserId(userId);
        run.setType(SimulationType.DEBT_PAYOFF);
        run.setStatus(SimulationStatus.RUNNING);
        run.setPlanType(plan);
        run.setTerminationReason(TerminationReason.NONE);
        run.setComparisonGroupId(group);
        run.setStartMonth(params.startMonth());
        run.setHorizonMonths(params.horizonMonths());
        run.setExtraPayment(params.extraPayment());
        run.setLumpSumPayment(params.lumpSumPayment());
        run.setLumpSumMonth(params.lumpSumMonth());
        run.setCreatedAt(OffsetDateTime.now());
        run.setUpdatedAt(OffsetDateTime.now());
        run = simulationRepository.save(run);

        persistSnapshot(run.getId(), debts);

        PlanTimeline timeline = planRunner.run(debts, plan, run.getId(), params);
        persistMonths(run.getId(), timeline);

        run.setStatus(timeline.status());
        run.setTerminationReason(timeline.terminationReason());
        run.setTotalInterest(timeline.totalInterest());
        run.setTotalPaid(timeline.totalPaid());
        run.setFinalBalance(timeline.finalBalance());
        run.setMonthsToPayoff(timeline.monthsToPayoff());
        run.setPayoffDate(timeline.payoffDate() == null
                ? null
                : timeline.payoffDate().atStartOfDay().atOffset(ZoneOffset.UTC));
        run.setPeakUtilization(timeline.peakUtilizationPct());
        run.setUpdatedAt(OffsetDateTime.now());
        simulationRepository.save(run);

        return timeline;
    }

    private void persistSnapshot(Long runId, List<DebtInput> debts) {
        List<SimulationRunDebt> rows = new ArrayList<>();
        for (DebtInput debt : debts) {
            SimulationRunDebt row = new SimulationRunDebt();
            row.setRunId(runId);
            row.setTomeId(debt.tomeId());
            row.setAccountName(debt.accountName());
            row.setStartingBalance(debt.currentBalance());
            row.setApr(debt.apr());
            row.setMinimumPayment(debt.minimumPayment());
            row.setCurrentPayment(debt.currentPayment());
            row.setCreditLimit(debt.creditLimit());
            rows.add(row);
        }
        runDebtRepository.saveAll(rows);
    }

    private void persistMonths(Long runId, PlanTimeline timeline) {
        List<SimulationMonth> months = new ArrayList<>();
        List<SimulationMonthDebt> debts = new ArrayList<>();

        for (MonthLedger month : timeline.months()) {
            SimulationMonth row = new SimulationMonth();
            row.setRunId(runId);
            row.setMonthIndex(month.monthIndex());
            row.setMonthLabel(month.monthLabel());
            row.setPeriodStart(month.periodStart());
            row.setStartingBalance(month.startingBalance());
            row.setMonthlyInterest(month.monthlyInterest());
            row.setMinimumDue(month.minimumDue());
            row.setActualPayment(month.actualPayment());
            row.setExtraPayment(month.extraPayment());
            row.setLumpSumApplied(month.lumpSumApplied());
            row.setRemainingBalance(month.remainingBalance());
            row.setUtilizationPct(month.utilizationPct());
            row.setInterestPaid(month.interestPaid());
            row.setAmountToCoverInterest(month.amountToCoverInterest());
            months.add(row);

            for (DebtMonthLedger debt : month.debts()) {
                SimulationMonthDebt debtRow = new SimulationMonthDebt();
                debtRow.setRunId(runId);
                debtRow.setMonthIndex(month.monthIndex());
                debtRow.setTomeId(debt.tomeId());
                debtRow.setAccountName(debt.accountName());
                debtRow.setStartingBalance(debt.startingBalance());
                debtRow.setMonthlyInterest(debt.monthlyInterest());
                debtRow.setMinimumDue(debt.minimumDue());
                debtRow.setActualPayment(debt.actualPayment());
                debtRow.setExtraPayment(debt.extraPayment());
                debtRow.setLumpSumApplied(debt.lumpSumApplied());
                debtRow.setRemainingBalance(debt.remainingBalance());
                debtRow.setUtilizationPct(debt.utilizationPct());
                debtRow.setInterestPaid(debt.interestPaid());
                debtRow.setAmountToCoverInterest(debt.amountToCoverInterest());
                debts.add(debtRow);
            }
        }

        monthRepository.saveAll(months);
        monthDebtRepository.saveAll(debts);
    }

    // ------------------------------------------------------------- read back

    private PlanTimeline toTimeline(Simulation run) {
        List<SimulationMonth> months = monthRepository
                .findByRunIdOrderByMonthIndexAsc(run.getId());
        Map<Integer, List<SimulationMonthDebt>> debtsByMonth = new LinkedHashMap<>();
        for (SimulationMonthDebt debt : monthDebtRepository
                .findByRunIdOrderByMonthIndexAscTomeIdAsc(run.getId())) {
            debtsByMonth.computeIfAbsent(debt.getMonthIndex(), key -> new ArrayList<>()).add(debt);
        }

        List<MonthLedger> monthLedgers = months.stream()
                .map(month -> new MonthLedger(
                        month.getMonthIndex(),
                        month.getMonthLabel(),
                        month.getPeriodStart(),
                        month.getStartingBalance(),
                        month.getMonthlyInterest(),
                        month.getMinimumDue(),
                        month.getActualPayment(),
                        month.getExtraPayment(),
                        month.getLumpSumApplied(),
                        month.getRemainingBalance(),
                        month.getUtilizationPct(),
                        month.getInterestPaid(),
                        month.getAmountToCoverInterest(),
                        debtsByMonth.getOrDefault(month.getMonthIndex(), List.of())
                                .stream()
                                .map(this::toDebtLedger)
                                .toList()))
                .toList();

        return new PlanTimeline(
                run.getId(),
                run.getPlanType(),
                run.getPlanType() == null ? "PLAN" : run.getPlanType().name(),
                run.getStatus(),
                run.getTerminationReason(),
                run.getMonthsToPayoff() == null ? 0 : run.getMonthsToPayoff(),
                run.getPayoffDate() == null ? null : run.getPayoffDate().toLocalDate(),
                run.getTotalInterest(),
                run.getTotalPaid(),
                run.getFinalBalance(),
                run.getPeakUtilization(),
                monthLedgers);
    }

    private DebtMonthLedger toDebtLedger(SimulationMonthDebt debt) {
        return new DebtMonthLedger(
                debt.getTomeId(),
                debt.getAccountName(),
                debt.getStartingBalance(),
                debt.getMonthlyInterest(),
                debt.getMinimumDue(),
                debt.getActualPayment(),
                debt.getExtraPayment(),
                debt.getLumpSumApplied(),
                debt.getRemainingBalance(),
                debt.getUtilizationPct(),
                debt.getInterestPaid(),
                debt.getAmountToCoverInterest());
    }

    private BigDecimal totalBalanceFromSnapshot(Long runId) {
        return runDebtRepository.findByRunId(runId).stream()
                .map(SimulationRunDebt::getStartingBalance)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
    }

    // ------------------------------------------------------------- loading

    private List<DebtInput> loadDebts(List<String> tomeIds, String userId) {
        List<Tome> tomes;
        if (tomeIds == null || tomeIds.isEmpty()) {
            tomes = scribe.findByUserId(userId);
        } else {
            tomes = new ArrayList<>();
            // Duplicate ids would duplicate the snapshot rows and violate
            // uq_simulation_run_debt; deduplicate while preserving order.
            for (String id : new LinkedHashSet<>(tomeIds)) {
                Tome tome = scribe.findById(id)
                        .orElseThrow(() -> new NoSuchElementException("Account not found"));
                if (!userId.equals(tome.getUserId())) {
                    throw new NoSuchElementException("Account not found");
                }
                tomes.add(tome);
            }
        }

        tomes.sort(Comparator.comparing(Tome::getId));
        List<DebtInput> debts = new ArrayList<>(tomes.size());
        for (Tome tome : tomes) {
            debts.add(toDebtInput(tome));
        }
        return debts;
    }

    private DebtInput toDebtInput(Tome tome) {
        BigDecimal minimum = money(tome.getMinimumDebtPayment());
        BigDecimal currentPayment = tome.getCurrentPayment() == null
                ? minimum
                : money(tome.getCurrentPayment());

        if (currentPayment.compareTo(minimum) < 0) {
            throw new IllegalArgumentException(
                    "currentPayment must be at least the minimum for " + tome.getName());
        }

        BigDecimal apr = tome.getApr() != null
                ? tome.getApr()
                : tome.getEstimatedApr() == null ? BigDecimal.ZERO : tome.getEstimatedApr();

        return new DebtInput(
                tome.getId(),
                tome.getName(),
                money(tome.getCurrentBalance()),
                apr,
                minimum,
                currentPayment,
                tome.getCreditLimit());
    }

    private static int horizon(Integer requested) {
        if (requested == null) {
            return DEFAULT_HORIZON;
        }
        if (requested < 1 || requested > MAX_HORIZON) {
            throw new IllegalArgumentException("horizonMonths must be between 1 and " + MAX_HORIZON);
        }
        return requested;
    }

    private static int lumpSumMonth(Integer requested, int horizon) {
        int month = requested == null ? 1 : requested;
        if (month < 1 || month > horizon) {
            throw new IllegalArgumentException("lumpSumMonth must be within the horizon");
        }
        return month;
    }

    private static BigDecimal nonNegative(BigDecimal value, String field) {
        BigDecimal result = value == null ? BigDecimal.ZERO : value;
        if (result.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException(field + " must be zero or more");
        }
        return result.setScale(2, RoundingMode.HALF_UP);
    }

    private static LocalDate startMonth(LocalDate requested) {
        LocalDate month = requested == null ? LocalDate.now() : requested;
        return month.withDayOfMonth(1);
    }

    private static BigDecimal money(BigDecimal value) {
        return (value == null ? BigDecimal.ZERO : value).setScale(2, RoundingMode.HALF_UP);
    }

    private static BigDecimal totalBalance(List<DebtInput> debts) {
        return debts.stream()
                .map(DebtInput::currentBalance)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
    }
}
