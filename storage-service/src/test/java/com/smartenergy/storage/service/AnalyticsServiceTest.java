package com.smartenergy.storage.service;

import com.smartenergy.storage.cassandra.entity.MeasurementEntity;
import com.smartenergy.storage.cassandra.service.CassandraMeasurementService;
import com.smartenergy.storage.mongo.document.SensorDocument;
import com.smartenergy.storage.mongo.service.SensorService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AnalyticsServiceTest {
    @Mock CassandraMeasurementService cassandra;
    @Mock SensorService sensors;

    private static final Instant FROM = Instant.parse("2026-10-28T00:00:00Z");
    private static final Instant TO = Instant.parse("2026-10-28T00:02:00Z");

    @Test
    void calculatesSummaryAndListsThresholdAnomalies() {
        SensorDocument sensor = new SensorDocument();
        sensor.setSensorId("sensor-001");
        sensor.setMaxPowerKw(2.0);
        sensor.setSamplingIntervalSeconds(60);
        when(sensors.findDocument("sensor-001")).thenReturn(sensor);
        MeasurementEntity first = measurement(FROM, 1.0, 230.0, 25.0);
        MeasurementEntity second = measurement(FROM.plusSeconds(60), 3.0, 230.0, 25.0);
        when(cassandra.findByRange("sensor-001", FROM, TO)).thenReturn(List.of(second, first));

        AnalyticsService service = new AnalyticsService(cassandra, sensors);
        AnalyticsSummary summary = service.summarize("sensor-001", FROM, TO);
        List<MeasurementAnomaly> anomalies = service.findAnomalies("sensor-001", FROM, TO);

        assertThat(summary.measurementCount()).isEqualTo(2);
        assertThat(summary.minPowerKw()).isEqualTo(1.0);
        assertThat(summary.avgPowerKw()).isEqualTo(2.0);
        assertThat(summary.maxPowerKw()).isEqualTo(3.0);
        assertThat(summary.energyKwh()).isEqualTo(0.066667);
        assertThat(summary.anomalyCount()).isEqualTo(1);
        assertThat(anomalies).hasSize(1);
        assertThat(anomalies.get(0).measurement().getMeasuredAt()).isEqualTo(FROM.plusSeconds(60));
        assertThat(anomalies.get(0).reasons()).containsExactly("POWER_ABOVE_MAX");
    }

    @Test
    void zeroDurationWindowHasZeroEnergy() {
        SensorDocument sensor = new SensorDocument();
        when(sensors.findDocument("sensor-001")).thenReturn(sensor);
        when(cassandra.findByRange("sensor-001", FROM, FROM))
                .thenReturn(List.of(measurement(FROM, 6, 230, null)));
        assertThat(new AnalyticsService(cassandra, sensors)
                .summarize("sensor-001", FROM, FROM).energyKwh()).isZero();
    }

    @Test
    void rejectsInvertedOrExcessiveRangeBeforeDatabaseRead() {
        AnalyticsService service = new AnalyticsService(cassandra, sensors);

        assertThatThrownBy(() -> service.summarize("sensor-001", TO, FROM))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.findAnomalies("sensor-001", FROM,
                FROM.plusSeconds(32L * 24 * 3600)))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(cassandra, sensors);
    }

    private MeasurementEntity measurement(Instant at, double power, double voltage, Double temperature) {
        return new MeasurementEntity("sensor-001", LocalDate.of(2026, 10, 28), at,
                power, voltage, temperature);
    }
}
