package com.krath.utilisim.ledger.vault;

import com.krath.utilisim.ledger.leaf.create.TomeResponse;
import com.krath.utilisim.ledger.tome.Tome;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TomeMapperTest {

    private BigDecimal bd(String value) {
        return new BigDecimal(value);
    }

    private Tome tome() {
        Tome tome = new Tome();
        tome.setId("t1");
        tome.setUserId("u1");
        tome.setName("Visa");
        tome.setCurrentBalance(bd("1200.00"));
        tome.setApr(bd("22.0000"));
        tome.setEstimatedApr(bd("22.0000"));
        tome.setMinimumDebtPayment(bd("35.00"));
        tome.setCurrentPayment(bd("35.00"));
        tome.setCreditLimit(bd("3000.00"));
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
        assertEquals(bd("1200.00"), response.currentBalance());
        assertEquals(bd("22.0000"), response.apr());
        assertEquals(bd("22.0000"), response.estimatedApr());
        assertEquals(bd("35.00"), response.minimumDebtPayment());
        assertEquals(bd("35.00"), response.currentPayment());
        assertEquals(bd("3000.00"), response.creditLimit());
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
