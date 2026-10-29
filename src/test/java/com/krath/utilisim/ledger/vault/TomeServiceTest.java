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

    private TomeRequest request(Float apr) {
        return new TomeRequest("Visa", 1200f, apr, 35f, 3000f, "CREDIT_CARD");
    }

    @Test
    @DisplayName("createTome copies the request and stores timestamps")
    void createsTome() {
        when(scribe.save(any(Tome.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Tome created = service.createTome(request(22f), "user-1");

        assertEquals("user-1", created.getUserId());
        assertEquals("Visa", created.getName());
        assertEquals(1200f, created.getCurrentBalance());
        assertEquals(22f, created.getApr());
        assertEquals(22f, created.getEstimatedApr());
        assertEquals(35f, created.getMinimumDebtPayment());
        assertEquals(3000f, created.getCreditLimit());
        assertEquals("CREDIT_CARD", created.getType());
        assertNotNull(created.getCreatedAt());
        assertNotNull(created.getUpdatedAt());
    }

    @Test
    @DisplayName("createTome throws when APR is missing (known defect API-01)")
    void rejectsMissingApr() {
        // Tome.apr is a primitive float, so TomeService.createTome unboxes a null
        // Float before it can fall back to the estimated APR.
        assertThrows(NullPointerException.class, () -> service.createTome(request(null), "user-1"));
        verify(scribe, never()).save(any(Tome.class));
    }

    @Test
    @DisplayName("calculateEstimatedApr returns the 16% placeholder")
    void estimatesAprAtSixteenPercent() {
        assertEquals(
                0.16,
                (double) ReflectionTestUtils.invokeMethod(service, "calculateEstimatedApr", request(null)),
                0.0001);
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
