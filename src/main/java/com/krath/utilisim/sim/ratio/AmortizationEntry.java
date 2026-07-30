package com.krath.utilisim.sim.ratio;

import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;

@Getter
@Setter
public class AmortizationEntry {
    private int monthIndex;
    private OffsetDateTime date;

    private float payment;
    private float principalPaid;
    private float interestPaid;
    private float remainingBalance;
}
