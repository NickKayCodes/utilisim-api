package com.krath.utilisim.ledger.leaf.create;

public record TomeRequest(
        String name,
        float currentBalance,
        Float apr,               // nullable
        float minimumDebtPayment,
        float creditLimit,
        String type
) {}