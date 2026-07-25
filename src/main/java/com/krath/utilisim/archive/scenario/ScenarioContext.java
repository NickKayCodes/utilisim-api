package com.krath.utilisim.archive.scenario;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.*;

import java.time.OffsetDateTime;

@Entity
@Data
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
public class ScenarioContext {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;
    private String userId;
    private String name;
    private String strategy; //Payment Strategy
    private float extraPayment;
    private float lumpSumPayment;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
