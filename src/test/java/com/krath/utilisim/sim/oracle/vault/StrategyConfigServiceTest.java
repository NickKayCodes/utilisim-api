package com.krath.utilisim.sim.oracle.vault;

import com.krath.utilisim.sim.forge.materials.StrategyType;
import com.krath.utilisim.sim.oracle.StrategyInput;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StrategyConfigServiceTest {

    @Mock
    private StrategyConfigRepository repository;

    @Mock
    private StrategyMapper mapper;

    @InjectMocks
    private StrategyConfigService service;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "strategyConfigRepository", repository);
        ReflectionTestUtils.setField(service, "strategyMapper", mapper);
    }

    private StrategyConfig config(String id) {
        StrategyConfig config = new StrategyConfig();
        config.setId(id);
        config.setUserId("u1");
        config.setScenarioId("s1");
        config.setStrategyType(StrategyType.SNOWBALL);
        config.setDescription("old");
        return config;
    }

    @Test
    @DisplayName("createStrategyConfig maps and saves")
    void createsConfig() {
        StrategyInput input = new StrategyInput(StrategyType.SNOWBALL, null, 10f, 20f);
        StrategyConfig mapped = config("cfg-1");
        when(mapper.toStrategyConfig(input, "u1", "s1", "why")).thenReturn(mapped);
        when(repository.save(mapped)).thenReturn(mapped);

        assertSame(mapped, service.createStrategyConfig(input, "u1", "s1", "why"));
    }

    @Test
    @DisplayName("lookup methods delegate to the repository")
    void delegatesLookups() {
        StrategyConfig config = config("cfg-1");
        when(repository.findByScenarioId("s1")).thenReturn(Optional.of(config));
        when(repository.findByUserId("u1")).thenReturn(List.of(config));
        when(repository.findById("cfg-1")).thenReturn(Optional.of(config));

        assertEquals(Optional.of(config), service.getByScenarioId("s1"));
        assertEquals(List.of(config), service.getUserConfigurations("u1"));
        assertEquals(Optional.of(config), service.getById("cfg-1"));
    }

    @Test
    @DisplayName("updateStrategyConfig updates editable fields")
    void updatesConfig() {
        StrategyConfig existing = config("cfg-1");
        StrategyInput oldInput = new StrategyInput(
                StrategyType.CUSTOM, List.of("t1"), 0f, 0f);
        StrategyInput update = new StrategyInput(
                StrategyType.AVALANCHE, List.of("t2", "t3"), 50f, 100f);

        when(repository.findById("cfg-1")).thenReturn(Optional.of(existing));
        when(mapper.toStrategyInput(existing)).thenReturn(oldInput);
        when(repository.save(existing)).thenReturn(existing);

        StrategyConfig updated = service.updateStrategyConfig("cfg-1", update, "new");

        assertEquals(StrategyType.AVALANCHE, updated.getStrategyType());
        assertEquals("[t2, t3]", updated.getCustomDebtOrder());
        assertEquals(50f, updated.getExtraPayment());
        assertEquals(100f, updated.getLumpSumPayment());
        assertEquals("new", updated.getDescription());
        assertNotNull(updated.getUpdatedAt());
    }

    @Test
    @DisplayName("updateStrategyConfig clears the custom order when none is stored")
    void updatesWithoutCustomOrder() {
        StrategyConfig existing = config("cfg-1");
        StrategyInput oldInput = new StrategyInput(StrategyType.SNOWBALL, null, 0f, 0f);
        StrategyInput update = new StrategyInput(StrategyType.HYBRID, null, 5f, 0f);

        when(repository.findById("cfg-1")).thenReturn(Optional.of(existing));
        when(mapper.toStrategyInput(existing)).thenReturn(oldInput);
        when(repository.save(existing)).thenReturn(existing);

        StrategyConfig updated = service.updateStrategyConfig("cfg-1", update, "new");

        assertNull(updated.getCustomDebtOrder());
    }

    @Test
    @DisplayName("updateStrategyConfig returns null when the config is missing")
    void updatesMissingConfig() {
        when(repository.findById("cfg-1")).thenReturn(Optional.empty());

        assertNull(service.updateStrategyConfig(
                "cfg-1", new StrategyInput(StrategyType.SNOWBALL, null, 0f, 0f), "new"));
        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("deleteStrategyConfig deletes by id")
    void deletesConfig() {
        service.deleteStrategyConfig("cfg-1");

        verify(repository).deleteById("cfg-1");
    }
}
