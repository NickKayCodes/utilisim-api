package com.krath.utilisim.ledger.vault;

import com.krath.utilisim.ledger.leaf.create.TomeResponse;
import com.krath.utilisim.ledger.tome.Tome;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TomeMapperTest {

    private Tome tome() {
        Tome tome = new Tome();
        tome.setId("t1");
        tome.setUserId("u1");
        tome.setName("Visa");
        tome.setCurrentBalance(1200f);
        tome.setApr(22f);
        tome.setEstimatedApr(22f);
        tome.setMinimumDebtPayment(35f);
        tome.setCreditLimit(3000f);
        tome.setType("CREDIT_CARD");
        tome.setCreatedAt(OffsetDateTime.parse("2026-01-01T00:00:00Z"));
        tome.setUpdatedAt(OffsetDateTime.parse("2026-01-02T00:00:00Z"));
        return tome;
    }

    @Test
    @DisplayName("toResponse maps every field")
    void mapsTome() {
        TomeResponse response = TomeMapper.toResponse(tome());

        assertEquals("t1", response.id());
        assertEquals("u1", response.userId());
        assertEquals("Visa", response.name());
        assertEquals(1200f, response.currentBalance());
        assertEquals(22f, response.apr());
        assertEquals(22f, response.estimatedApr());
        assertEquals(35f, response.minimumDebtPayment());
        assertEquals(3000f, response.creditLimit());
        assertEquals("CREDIT_CARD", response.type());
        assertEquals(OffsetDateTime.parse("2026-01-01T00:00:00Z"), response.createdAt());
        assertEquals(OffsetDateTime.parse("2026-01-02T00:00:00Z"), response.updatedAt());
    }

    @Test
    @DisplayName("toResponseList maps every tome")
    void mapsList() {
        List<TomeResponse> responses = TomeMapper.toResponseList(List.of(tome(), tome()));

        assertEquals(2, responses.size());
        assertTrue(responses.stream().allMatch(response -> "t1".equals(response.id())));
    }

    @Test
    @DisplayName("toResponseList handles an empty list")
    void mapsEmptyList() {
        assertTrue(TomeMapper.toResponseList(List.of()).isEmpty());
    }
}
