package com.krath.utilisim.sim.forge.anvil;

import com.krath.utilisim.sim.chronos.ChronosService;
import com.krath.utilisim.sim.forge.materials.PlanType;
import com.krath.utilisim.sim.forge.materials.TerminationReason;
import com.krath.utilisim.sim.ratio.UtilizationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the formulas and business rules in CURRENT_PLAN_DESIGN §3.4/§3.5.
 *
 * <p>The reference workbook is not available, so these assert the documented
 * arithmetic rather than spreadsheet cells. Workbook-specific calibration
 * (18% APR, $25,000 limit, payment = minimum) lives in the fixture helpers and
 * is noted where used.</p>
 */
class PayoffLedgerBuilderTest {

    private static final LocalDate START = LocalDate.of(2026, 6, 1);

    private final PayoffLedgerBuilder builder =
            new PayoffLedgerBuilder(new UtilizationService(), new ChronosService());

    private DebtInput debt(
            String id,
            String balance,
            String apr,
            String minimum,
            String currentPayment,
            String limit
    ) {
        return new DebtInput(
                id,
                id,
                new BigDecimal(balance),
                new BigDecimal(apr),
                new BigDecimal(minimum),
                new BigDecimal(currentPayment),
                limit == null ? null : new BigDecimal(limit));
    }

    private PayoffLedgerBuilder.Params params(String extra, String lump, int lumpMonth, int horizon) {
        return new PayoffLedgerBuilder.Params(
                new BigDecimal(extra),
                new BigDecimal(lump),
                lumpMonth,
                horizon,
                START);
    }

    @Test
    @DisplayName("month one reproduces the documented formula: interest then payment")
    void reproducesDocumentedFormula() {
        DebtInput visa = debt("t1", "1200", "12", "100", "100", "3000");

        var result = builder.build(List.of(visa), PlanType.CURRENT, List.of(visa),
                params("0", "0", 1, 60));

        assertEquals(TerminationReason.PAID_OFF, result.reason());
        assertEquals(13, result.months().size());

        var first = result.months().get(0);
        assertEquals(new BigDecimal("1200.00"), first.startingBalance());
        assertEquals(new BigDecimal("12.00"), first.monthlyInterest());
        assertEquals(new BigDecimal("100.00"), first.minimumDue());
        assertEquals(new BigDecimal("100.00"), first.actualPayment());
        assertEquals(new BigDecimal("1112.00"), first.remainingBalance());
        assertEquals(new BigDecimal("37.07"), first.utilizationPct());
        assertEquals(new BigDecimal("12.00"), first.amountToCoverInterest());
        assertEquals("Jun-26", first.monthLabel());
        assertEquals(START, first.periodStart());
    }

    @Test
    @DisplayName("interest is rounded to cents each month")
    void roundsMonthlyInterest() {
        DebtInput debt = debt("t1", "1000", "19.99", "50", "50", "2000");

        var first = builder.build(List.of(debt), PlanType.CURRENT, List.of(debt),
                params("0", "0", 1, 1)).months().get(0);

        // 1000 * 19.99 / 1200 = 16.6583 -> 16.66
        assertEquals(new BigDecimal("16.66"), first.monthlyInterest());
    }

    @Test
    @DisplayName("CURRENT equals MINIMUM when the current payment is the minimum")
    void currentEqualsMinimumWhenPaymentsMatch() {
        DebtInput visa = debt("t1", "1200", "12", "100", "100", "3000");

        var current = builder.build(List.of(visa), PlanType.CURRENT, List.of(visa),
                params("0", "0", 1, 60));
        var minimum = builder.build(List.of(visa), PlanType.MINIMUM, List.of(visa),
                params("0", "0", 1, 60));

        assertEquals(minimum.months(), current.months());
        assertEquals(minimum.months().get(0).remainingBalance(),
                current.months().get(0).remainingBalance());
    }

    @Test
    @DisplayName("a current payment above the minimum pays the debt off sooner")
    void currentPaymentAboveMinimumPaysFaster() {
        DebtInput aggressive = debt("t1", "1200", "12", "100", "300", "3000");
        DebtInput minimum = debt("t1", "1200", "12", "100", "100", "3000");

        var fast = builder.build(List.of(aggressive), PlanType.CURRENT, List.of(aggressive),
                params("0", "0", 1, 60));
        var slow = builder.build(List.of(minimum), PlanType.MINIMUM, List.of(minimum),
                params("0", "0", 1, 60));

        assertTrue(fast.months().size() < slow.months().size());
    }

    @Test
    @DisplayName("CURRENT ignores extra and lump-sum inputs (D1)")
    void currentIgnoresStrategyInputs() {
        DebtInput visa = debt("t1", "1200", "12", "100", "100", "3000");

        var plain = builder.build(List.of(visa), PlanType.CURRENT, List.of(visa),
                params("0", "0", 1, 60));
        var withInputs = builder.build(List.of(visa), PlanType.CURRENT, List.of(visa),
                params("500", "1000", 1, 60));

        assertEquals(plain.months().size(), withInputs.months().size());
        assertEquals(plain.months().get(0).remainingBalance(),
                withInputs.months().get(0).remainingBalance());
        assertEquals(new BigDecimal("0.00"), withInputs.months().get(0).lumpSumApplied());
    }

    @Test
    @DisplayName("MINIMUM ignores extra and lump-sum inputs (D1)")
    void minimumIgnoresStrategyInputs() {
        DebtInput visa = debt("t1", "1200", "12", "100", "100", "3000");

        var plain = builder.build(List.of(visa), PlanType.MINIMUM, List.of(visa),
                params("0", "0", 1, 60));
        var withInputs = builder.build(List.of(visa), PlanType.MINIMUM, List.of(visa),
                params("500", "1000", 1, 60));

        assertEquals(plain.months().size(), withInputs.months().size());
        assertEquals(new BigDecimal("0.00"), withInputs.months().get(0).lumpSumApplied());
    }

    @Test
    @DisplayName("a strategy plans applies the lump sum only in its chosen month")
    void appliesLumpSumInChosenMonth() {
        DebtInput visa = debt("t1", "5000", "12", "100", "100", "6000");

        var early = builder.build(List.of(visa), PlanType.AVALANCHE, List.of(visa),
                params("0", "200", 1, 120));
        var later = builder.build(List.of(visa), PlanType.AVALANCHE, List.of(visa),
                params("0", "200", 12, 120));

        assertEquals(new BigDecimal("200.00"), early.months().get(0).lumpSumApplied());
        assertEquals(new BigDecimal("0.00"), later.months().get(0).lumpSumApplied());
        assertEquals(new BigDecimal("200.00"), later.months().get(11).lumpSumApplied());
        assertTrue(early.months().size() < later.months().size());
    }

    @Test
    @DisplayName("avalanche costs no more interest than snowball when surplus exists")
    void avalancheBeatsSnowball() {
        DebtInput high = debt("high", "1000", "30", "25", "25", "2000");
        DebtInput low = debt("low", "500", "5", "25", "25", "1000");

        var avalanche = builder.build(
                List.of(high, low), PlanType.AVALANCHE, List.of(high, low),
                params("100", "0", 1, 600));
        var snowball = builder.build(
                List.of(high, low), PlanType.SNOWBALL, List.of(low, high),
                params("100", "0", 1, 600));

        BigDecimal avalancheInterest = totalInterest(avalanche);
        BigDecimal snowballInterest = totalInterest(snowball);

        assertTrue(avalancheInterest.compareTo(snowballInterest) < 0,
                "avalanche=" + avalancheInterest + " snowball=" + snowballInterest);
    }

    @Test
    @DisplayName("a term that ends with a balance returns HORIZON_REACHED")
    void reportsHorizonReached() {
        DebtInput debt = debt("t1", "1000", "18", "25", "25", "2000");

        var result = builder.build(List.of(debt), PlanType.CURRENT, List.of(debt),
                params("0", "0", 1, 12));

        assertEquals(TerminationReason.HORIZON_REACHED, result.reason());
        assertEquals(12, result.months().size());
        assertTrue(result.months().get(11).remainingBalance().compareTo(BigDecimal.ZERO) > 0);
    }

    @Test
    @DisplayName("a payment that only covers interest returns STAGNANT after one month")
    void reportsStagnant() {
        DebtInput debt = debt("t1", "1000", "18", "10", "10", "2000");

        var result = builder.build(List.of(debt), PlanType.CURRENT, List.of(debt),
                params("0", "0", 1, 12));

        // 1000 * 18 / 1200 = 15.00 interest > 10.00 payment
        assertEquals(TerminationReason.STAGNANT, result.reason());
        assertEquals(1, result.months().size());
        assertEquals(new BigDecimal("1005.00"), result.months().get(0).remainingBalance());
    }

    @Test
    @DisplayName("utilization is null when no credit limit is set")
    void utilizationIsNullWithoutLimit() {
        DebtInput debt = debt("t1", "1000", "12", "100", "100", null);

        var first = builder.build(List.of(debt), PlanType.CURRENT, List.of(debt),
                params("0", "0", 1, 1)).months().get(0);

        assertNull(first.utilizationPct());
    }

    @Test
    @DisplayName("the aggregate row equals the sum of the per-debt rows")
    void aggregatesPerDebtRows() {
        DebtInput a = debt("a", "1000", "12", "50", "50", "2000");
        DebtInput b = debt("b", "500", "24", "25", "25", "1000");

        var first = builder.build(List.of(a, b), PlanType.CURRENT, List.of(a, b),
                params("0", "0", 1, 1)).months().get(0);

        assertEquals(new BigDecimal("1500.00"), first.startingBalance());
        assertEquals(new BigDecimal("20.00"), first.monthlyInterest());
        assertEquals(new BigDecimal("75.00"), first.actualPayment());
        assertEquals(new BigDecimal("1445.00"), first.remainingBalance());
        assertEquals(2, first.debts().size());
        assertEquals(first.monthlyInterest(), first.amountToCoverInterest());
    }

    @Test
    @DisplayName("actualPayment and lumpSumApplied are reported separately")
    void separatesActualPaymentAndLumpSum() {
        DebtInput debt = debt("t1", "1000", "0", "100", "100", "1000");

        var first = builder.build(List.of(debt), PlanType.AVALANCHE, List.of(debt),
                params("0", "300", 1, 6)).months().get(0);

        assertEquals(new BigDecimal("100.00"), first.actualPayment());
        assertEquals(new BigDecimal("300.00"), first.lumpSumApplied());
        assertEquals(new BigDecimal("600.00"), first.remainingBalance());
        assertEquals(0, first.startingBalance()
                .add(first.monthlyInterest())
                .subtract(first.actualPayment())
                .subtract(first.lumpSumApplied())
                .subtract(first.remainingBalance())
                .compareTo(BigDecimal.ZERO));
    }

    @ParameterizedTest(name = "invariants hold for {0}")
    @EnumSource(PlanType.class)
    @DisplayName("I-001/I-003/I-004/I-007 hold for every plan and month")
    void ledgerInvariantsHoldForEveryPlanAndMonth(PlanType plan) {
        List<DebtInput> debts = List.of(
                debt("debt-a", "1500", "18", "50", "60", "3000"),
                debt("debt-b", "800", "24", "30", "35", "2000"),
                debt("debt-c", "2500", "9", "45", "45", "4000"));
        PayoffLedgerBuilder.Params params = params("75", "200", 3, 120);
        List<DebtInput> order = plan.isStrategy()
                ? List.of(debts.get(2), debts.get(0), debts.get(1))
                : debts;

        PayoffLedgerBuilder.LedgerResult result = builder.build(debts, plan, order, params);
        assertFalse(result.months().isEmpty(), "plan " + plan + " produced no months");

        for (PayoffLedgerBuilder.LedgerMonth month : result.months()) {
            BigDecimal allocated = sum(month.debts(), PayoffLedgerBuilder.LedgerDebtMonth::actualPayment)
                    .add(sum(month.debts(), PayoffLedgerBuilder.LedgerDebtMonth::lumpSumApplied));
            BigDecimal available = availableFunds(debts, plan, month.monthIndex(), params);

            assertAll("I-001 plan=" + plan + " month=" + month.monthIndex(),
                    () -> assertTrue(allocated.compareTo(available) <= 0,
                            () -> "I-001 plan=" + plan + " month=" + month.monthIndex()
                                    + " allocated=" + allocated + " available=" + available));

            BigDecimal debtRemaining = sum(month.debts(), PayoffLedgerBuilder.LedgerDebtMonth::remainingBalance);
            assertAll("I-007 plan=" + plan + " month=" + month.monthIndex(),
                    () -> assertEquals(0, month.remainingBalance().compareTo(debtRemaining),
                            () -> "I-007 plan=" + plan + " month=" + month.monthIndex()
                                    + " aggregate=" + month.remainingBalance()
                                    + " sumDebts=" + debtRemaining));

            for (PayoffLedgerBuilder.LedgerDebtMonth debtRow : month.debts()) {
                String where = describe(plan, month, debtRow);

                assertAll("I-003 plan=" + plan + " month=" + month.monthIndex()
                                + " debt=" + debtRow.tomeId(),
                        () -> assertTrue(debtRow.actualPayment().signum() >= 0,
                                () -> "I-003 actualPayment negative: " + where),
                        () -> assertTrue(debtRow.extraPayment().signum() >= 0,
                                () -> "I-003 extraPayment negative: " + where),
                        () -> assertTrue(debtRow.lumpSumApplied().signum() >= 0,
                                () -> "I-003 lumpSumApplied negative: " + where));

                BigDecimal obligation = debtRow.startingBalance().add(debtRow.monthlyInterest());
                BigDecimal allocation = debtRow.actualPayment().add(debtRow.lumpSumApplied());
                assertAll("I-004 plan=" + plan + " month=" + month.monthIndex()
                                + " debt=" + debtRow.tomeId(),
                        () -> assertTrue(allocation.compareTo(obligation) <= 0,
                                () -> "I-004 overpayment: " + where
                                        + " allocation=" + allocation + " obligation=" + obligation));
            }
        }
    }

    private static BigDecimal sum(
            List<PayoffLedgerBuilder.LedgerDebtMonth> debts,
            java.util.function.Function<PayoffLedgerBuilder.LedgerDebtMonth, BigDecimal> field
    ) {
        return debts.stream().map(field).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static BigDecimal availableFunds(
            List<DebtInput> debts,
            PlanType plan,
            int month,
            PayoffLedgerBuilder.Params params
    ) {
        BigDecimal available = BigDecimal.ZERO;
        for (DebtInput debt : debts) {
            if (debt.currentBalance().compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            available = available.add(plan == PlanType.CURRENT
                    ? debt.currentPayment()
                    : debt.minimumPayment());
        }
        if (plan.isStrategy()) {
            available = available.add(params.extraPayment());
            if (month == params.lumpSumMonth()) {
                available = available.add(params.lumpSumPayment());
            }
        }
        return available.setScale(2, RoundingMode.HALF_UP);
    }

    private static String describe(PlanType plan, PayoffLedgerBuilder.LedgerMonth month, PayoffLedgerBuilder.LedgerDebtMonth debt) {
        return "plan=" + plan + " month=" + month.monthIndex() + " debt=" + debt.tomeId();
    }

    private static BigDecimal totalInterest(PayoffLedgerBuilder.LedgerResult result) {
        return result.months().stream()
                .map(PayoffLedgerBuilder.LedgerMonth::monthlyInterest)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
