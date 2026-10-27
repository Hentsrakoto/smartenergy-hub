package com.smartenergy.storage.mongo.service;

import com.smartenergy.storage.cassandra.entity.MeasurementEntity;
import com.smartenergy.storage.cassandra.service.CassandraMeasurementService;
import com.smartenergy.storage.mongo.document.DailySummaryDocument;
import com.smartenergy.storage.mongo.document.SensorDocument;
import com.smartenergy.storage.mongo.dto.DailySummaryResponse;
import com.smartenergy.storage.mongo.exception.NoMeasurementsException;
import com.smartenergy.storage.mongo.exception.SensorNotFoundException;
import com.smartenergy.storage.mongo.repository.DailySummaryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DailySummaryServiceTest {

    private static final LocalDate DAY = LocalDate.of(2026, 10, 22);

    @Mock
    private DailySummaryRepository summaryRepository;

    @Mock
    private SensorService sensorService;

    @Mock
    private CassandraMeasurementService cassandraService;

    private DailySummaryService service;

    @BeforeEach
    void setUp() {
        service = new DailySummaryService(summaryRepository, sensorService, cassandraService);
    }

    @Test
    @DisplayName("getOrCompute() doit retourner l'agrégat stocké sans interroger Cassandra")
    void shouldReturnStoredSummaryWhenNotRefreshing() {
        when(sensorService.findDocument("SENSOR-001")).thenReturn(sensor("SENSOR-001"));
        when(summaryRepository.findBySensorIdAndDate("SENSOR-001", "2026-10-22"))
                .thenReturn(Optional.of(existingSummary()));

        DailySummaryResponse response = service.getOrCompute("SENSOR-001", DAY, false);

        assertThat(response.sensorId()).isEqualTo("SENSOR-001");
        assertThat(response.date()).isEqualTo("2026-10-22");
        assertThat(response.avgPowerKw()).isEqualTo(3.42);
        assertThat(response.maxPowerKw()).isEqualTo(7.81);
        assertThat(response.energyKwh()).isEqualTo(68.2);
        assertThat(response.anomalyCount()).isEqualTo(2);

        verifyNoInteractions(cassandraService);
        verify(summaryRepository, never()).save(any());
    }

    @Test
    @DisplayName("getOrCompute() doit calculer et stocker l'agrégat s'il est absent")
    void shouldComputeAndStoreMissingSummary() {
        when(sensorService.findDocument("SENSOR-001")).thenReturn(sensor("SENSOR-001"));
        when(summaryRepository.findBySensorIdAndDate("SENSOR-001", "2026-10-22"))
                .thenReturn(Optional.empty());
        when(cassandraService.findByDay("SENSOR-001", DAY)).thenReturn(List.of(
                measurement(0, 0, 2.0, 220.0, 20.0),
                measurement(0, 30, 4.0, 250.0, 20.0),
                measurement(1, 0, 12.0, 220.0, 35.0)
        ));

        DailySummaryResponse response = service.getOrCompute("SENSOR-001", DAY, false);

        assertThat(response.sensorId()).isEqualTo("SENSOR-001");
        assertThat(response.date()).isEqualTo("2026-10-22");
        assertThat(response.measurementCount()).isEqualTo(3);
        assertThat(response.minPowerKw()).isEqualTo(2.0);
        assertThat(response.avgPowerKw()).isEqualTo(6.0);
        assertThat(response.maxPowerKw()).isEqualTo(12.0);
        assertThat(response.energyKwh()).isCloseTo(3.2, within(1e-9));
        assertThat(response.anomalyCount()).isEqualTo(2);
        assertThat(response.updatedAt()).isNotNull();

        ArgumentCaptor<DailySummaryDocument> captor = ArgumentCaptor.forClass(DailySummaryDocument.class);
        verify(summaryRepository).save(captor.capture());
        DailySummaryDocument saved = captor.getValue();
        assertThat(saved.getId()).isEqualTo("SENSOR-001:2026-10-22");
        assertThat(saved.getSensorId()).isEqualTo("SENSOR-001");
        assertThat(saved.getDate()).isEqualTo("2026-10-22");
        assertThat(saved.getMeasurementCount()).isEqualTo(3);
        assertThat(saved.getAnomalyCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("refresh=true doit forcer le recalcul même si un agrégat existe")
    void shouldForceRecalculationWhenRefreshIsTrue() {
        when(sensorService.findDocument("SENSOR-001")).thenReturn(sensor("SENSOR-001"));
        when(cassandraService.findByDay("SENSOR-001", DAY)).thenReturn(List.of(
                measurement(0, 0, 5.0, 220.0, 20.0)
        ));

        DailySummaryResponse response = service.getOrCompute("SENSOR-001", DAY, true);

        assertThat(response.avgPowerKw()).isEqualTo(5.0);
        assertThat(response.measurementCount()).isEqualTo(1);
        assertThat(response.energyKwh()).isCloseTo(5.0 / 60.0, within(1e-6));

        verify(summaryRepository, never()).findBySensorIdAndDate(any(), any());
        verify(summaryRepository).save(any(DailySummaryDocument.class));
    }

    @Test
    @DisplayName("getOrCompute() doit renvoyer 404 si aucune mesure n'existe dans Cassandra")
    void shouldFailWhenNoMeasurementsExist() {
        when(sensorService.findDocument("SENSOR-001")).thenReturn(sensor("SENSOR-001"));
        when(summaryRepository.findBySensorIdAndDate("SENSOR-001", "2026-10-22"))
                .thenReturn(Optional.empty());
        when(cassandraService.findByDay("SENSOR-001", DAY)).thenReturn(List.of());

        assertThatThrownBy(() -> service.getOrCompute("SENSOR-001", DAY, false))
                .isInstanceOf(NoMeasurementsException.class)
                .hasMessageContaining("Aucune mesure");

        verify(summaryRepository, never()).save(any());
    }

    @Test
    @DisplayName("getOrCompute() doit propager l'absence de capteur sans appeler Cassandra")
    void shouldFailWhenSensorDoesNotExist() {
        when(sensorService.findDocument("UNKNOWN")).thenThrow(new SensorNotFoundException("UNKNOWN"));

        assertThatThrownBy(() -> service.getOrCompute("UNKNOWN", DAY, false))
                .isInstanceOf(SensorNotFoundException.class)
                .hasMessageContaining("UNKNOWN");

        verifyNoInteractions(cassandraService);
        verifyNoInteractions(summaryRepository);
    }

    @Test
    @DisplayName("les valeurs exactement aux seuils ne doivent pas être comptées comme anomalies")
    void shouldNotCountBoundaryValuesAsAnomalies() {
        when(sensorService.findDocument("SENSOR-001")).thenReturn(sensor("SENSOR-001"));
        when(summaryRepository.findBySensorIdAndDate("SENSOR-001", "2026-10-22"))
                .thenReturn(Optional.empty());
        when(cassandraService.findByDay("SENSOR-001", DAY)).thenReturn(List.of(
                measurement(0, 0, 10.0, 200.0, 30.0),
                measurement(0, 30, 10.0, 240.0, null),
                measurement(1, 0, 10.0, 220.0, 30.0)
        ));

        DailySummaryResponse response = service.getOrCompute("SENSOR-001", DAY, false);

        assertThat(response.anomalyCount()).isZero();
        assertThat(response.avgPowerKw()).isEqualTo(10.0);
        assertThat(response.minPowerKw()).isEqualTo(10.0);
        assertThat(response.maxPowerKw()).isEqualTo(10.0);
        assertThat(response.energyKwh()).isCloseTo(10.0 + 10.0 / 60.0, within(1e-6));
    }

    @Test
    @DisplayName("getOrCompute() doit refuser une date nulle")
    void shouldRejectNullDate() {
        assertThatThrownBy(() -> service.getOrCompute("SENSOR-001", null, false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("date est obligatoire");

        verifyNoInteractions(sensorService, cassandraService, summaryRepository);
    }

    private SensorDocument sensor(String sensorId) {
        SensorDocument sensor = new SensorDocument();
        sensor.setSensorId(sensorId);
        sensor.setName("Capteur " + sensorId);
        sensor.setMaxPowerKw(10.0);
        sensor.setActive(true);
        sensor.setMinVoltage(200.0);
        sensor.setMaxVoltage(240.0);
        sensor.setMaxTemperature(30.0);
        sensor.setSamplingIntervalSeconds(60);
        return sensor;
    }

    private MeasurementEntity measurement(int hour, int minute, double powerKw,
                                          double voltage, Double temperature) {
        Instant measuredAt = DAY.atTime(hour, minute).toInstant(ZoneOffset.UTC);
        return new MeasurementEntity("SENSOR-001", DAY, measuredAt, powerKw, voltage, temperature);
    }

    private DailySummaryDocument existingSummary() {
        DailySummaryDocument summary = new DailySummaryDocument();
        summary.setId("SENSOR-001:2026-10-22");
        summary.setSensorId("SENSOR-001");
        summary.setDate("2026-10-22");
        summary.setAvgPowerKw(3.42);
        summary.setMaxPowerKw(7.81);
        summary.setEnergyKwh(68.2);
        summary.setAnomalyCount(2);
        summary.setMeasurementCount(1440);
        summary.setMinPowerKw(1.0);
        summary.setUpdatedAt(Instant.parse("2026-10-22T23:59:00Z"));
        return summary;
    }
}
