package com.smartenergy.storage.service;

import com.smartenergy.storage.model.EnergyMeasurementEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InMemoryMeasurementPersistenceServiceTest {

    private InMemoryMeasurementPersistenceService service;

    @BeforeEach
    void setUp() {
        service = new InMemoryMeasurementPersistenceService();
    }

    @Test
    @DisplayName("Doit persister et stocker les mesures reçues")
    void shouldPersistValidEvents() {
        EnergyMeasurementEvent event1 = new EnergyMeasurementEvent("1-0", "sensor-1", Instant.now(), 20.0, 230.0, 25.0);
        EnergyMeasurementEvent event2 = new EnergyMeasurementEvent("2-0", "sensor-2", Instant.now(), 15.0, 229.0, null);

        service.persist(event1);
        service.persist(event2);

        assertThat(service.getPersistedCount()).isEqualTo(2);
        assertThat(service.getPersistedEvents()).containsExactly(event1, event2);
    }

    @Test
    @DisplayName("Doit lever une exception lorsque le mode échec simulé est activé")
    void shouldThrowWhenSimulationEnabled() {
        service.setSimulateFailure(true);
        EnergyMeasurementEvent event = new EnergyMeasurementEvent("1-0", "sensor-1", Instant.now(), 20.0, 230.0, 25.0);

        assertThatThrownBy(() -> service.persist(event))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Échec simulé");

        assertThat(service.getPersistedCount()).isZero();
    }
}
