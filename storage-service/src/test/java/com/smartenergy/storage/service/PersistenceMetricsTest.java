package com.smartenergy.storage.service;

import com.smartenergy.storage.cassandra.service.CassandraMeasurementService;
import com.smartenergy.storage.model.EnergyMeasurementEvent;
import com.smartenergy.storage.mongo.service.DailySummaryService;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class PersistenceMetricsTest {
    @Test
    void countsSuccessAndFailureWithoutSwallowingPersistenceErrors() {
        var cassandra = mock(CassandraMeasurementService.class);
        var summaries = mock(DailySummaryService.class);
        var registry = new SimpleMeterRegistry();
        var service = new CassandraPersistenceService(cassandra, summaries, registry);
        var event = new EnergyMeasurementEvent("1-0", "sensor-metrics", java.time.Instant.now(), 2, 230, null);
        service.persist(event);
        when(cassandra.save(event)).thenThrow(new IllegalStateException("offline"));
        assertThatThrownBy(() -> service.persist(event)).isInstanceOf(IllegalStateException.class);
        assertThat(registry.get("smartenergy.storage.persistence").tag("outcome", "success").timer().count()).isEqualTo(1);
        assertThat(registry.get("smartenergy.storage.persistence").tag("outcome", "error").timer().count()).isEqualTo(1);
        assertThat(registry.get("smartenergy.storage.persistence.errors").counter().count()).isEqualTo(1);
        assertThat(service.getPersistedCount()).isEqualTo(1);
    }
}
