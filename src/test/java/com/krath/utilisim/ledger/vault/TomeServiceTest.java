package com.krath.utilisim.ledger.vault;

import com.krath.utilisim.ledger.leaf.create.TomeRequest;
import com.krath.utilisim.ledger.scribe.Scribe;
import com.krath.utilisim.ledger.tome.Tome;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TomeServiceTest {

    @Mock
    private Scribe scribe;

    @InjectMocks
    private TomeService service;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "scribe", scribe);
    }

    private BigDecimal bd(String value) {
        return new BigDecimal(value);
    }

    private TomeRequest request(BigDecimal apr) {
        return request(apr, null);
    }

    private TomeRequest request(BigDecimal apr, BigDecimal currentPayment) {
        return new TomeRequest(
                "Visa", bd("1200"), apr, bd("35"), currentPayment, bd("3000"), "CREDIT_CARD");
    }

    @Test
    @DisplayName("createTome copies the request and stores timestamps")
    void createsTome() {
        when(scribe.save(any(Tome.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Tome created = service.createTome(request(bd("22")), "user-1");

        assertEquals("user-1", created.getUserId());
        assertEquals("Visa", created.getName());
        assertEquals(bd("1200.00"), created.getCurrentBalance());
        assertEquals(bd("22.0000"), created.getApr());
        assertEquals(bd("22.0000"), created.getEstimatedApr());
        assertEquals(bd("35.00"), created.getMinimumDebtPayment());
        assertEquals(bd("35.00"), created.getCurrentPayment());
        assertEquals(bd("3000.00"), created.getCreditLimit());
        assertEquals("CREDIT_CARD", created.getType());
        assertNotNull(created.getCreatedAt());
        assertNotNull(created.getUpdatedAt());
    }

    @Test
    @DisplayName("currentPayment defaults to the minimum when omitted")
    void defaultsCurrentPaymentToMinimum() {
        when(scribe.save(any(Tome.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Tome created = service.createTome(request(bd("22")), "user-1");

        assertEquals(created.getMinimumDebtPayment(), created.getCurrentPayment());
    }

    @Test
    @DisplayName("createTome rejects a current payment below the minimum")
    void rejectsCurrentPaymentBelowMinimum() {
        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> service.createTome(request(bd("22"), bd("10")), "user-1"));

        assertTrue(ex.getMessage().contains("below the minimum"));
        verify(scribe, never()).save(any(Tome.class));
    }

    @Test
    @DisplayName("a missing APR is accepted and falls back to the 16% estimate")
    void fallsBackToEstimatedApr() {
        when(scribe.save(any(Tome.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Tome created = service.createTome(request(null), "user-1");

        assertNull(created.getApr());
        assertEquals(bd("16.0000"), created.getEstimatedApr());
        verify(scribe).save(any(Tome.class));
    }

    @Test
    @DisplayName("the estimated APR placeholder is 16 percent, not 0.16 percent")
    void estimatesAprAtSixteenPercent() {
        // Units regression (API-07): the value is a percentage, so it is 16.0.
        assertEquals(16.0, TomeService.ESTIMATED_APR_PERCENT, 0.0001);
        assertEquals(16f, (float) TomeService.ESTIMATED_APR_PERCENT);
    }

    @Test
    @DisplayName("getAllTomesByUserId delegates to the repository")
    void listsTomes() {
        List<Tome> tomes = List.of(new Tome());
        when(scribe.findByUserId("user-1")).thenReturn(tomes);

        assertEquals(tomes, service.getAllTomesByUserId("user-1"));
    }

    @Test
    @DisplayName("deleteTomeById removes an owned tome")
    void deletesOwnedTome() {
        Tome tome = new Tome();
        tome.setId("t1");
        tome.setUserId("user-1");
        when(scribe.findById("t1")).thenReturn(Optional.of(tome));

        service.deleteTomeById("t1", "user-1");

        verify(scribe).delete(tome);
    }

    @Test
    @DisplayName("deleteTomeById rejects a missing tome")
    void rejectsMissingTome() {
        when(scribe.findById("t1")).thenReturn(Optional.empty());

        RuntimeException ex = assertThrows(
                RuntimeException.class, () -> service.deleteTomeById("t1", "user-1"));

        assertEquals("Not found", ex.getMessage());
        verify(scribe, never()).delete(any());
    }

    @Test
    @DisplayName("deleteTomeById rejects another user's tome")
    void rejectsForeignTome() {
        Tome tome = new Tome();
        tome.setUserId("someone-else");
        when(scribe.findById("t1")).thenReturn(Optional.of(tome));

        RuntimeException ex = assertThrows(
                RuntimeException.class, () -> service.deleteTomeById("t1", "user-1"));

        assertEquals("Forbidden", ex.getMessage());
        verify(scribe, never()).delete(any());
    }
}
