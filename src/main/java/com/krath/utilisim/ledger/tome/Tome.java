package com.krath.utilisim.ledger.tome;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Entity
@Table(name = "tome_ledger")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
public class Tome {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;
    private String userId; //fk to AppUser
    private String name; // Acc name
    private BigDecimal currentBalance; //current balance

    @Column(nullable = true)
    private BigDecimal apr; //interest rate that is user entered but can be nullable

    private BigDecimal estimatedApr; //if apr is null, system will calculate an estimated apr (not accurate)

    private BigDecimal minimumDebtPayment;

    /** What the user actually pays today; defaults to the minimum when omitted. */
    private BigDecimal currentPayment;

    private BigDecimal creditLimit;
    private String type; // Line of credit type.
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
