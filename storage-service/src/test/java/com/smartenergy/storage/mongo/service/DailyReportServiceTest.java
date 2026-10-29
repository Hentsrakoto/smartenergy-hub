package com.smartenergy.storage.mongo.service;

import com.smartenergy.storage.mongo.document.DailySummaryDocument;
import com.smartenergy.storage.mongo.document.SensorDocument;
import com.smartenergy.storage.mongo.dto.DailyReportResponse;
import com.smartenergy.storage.mongo.exception.NoMeasurementsException;
import com.smartenergy.storage.mongo.repository.DailyReportRepository;
import com.smartenergy.storage.mongo.repository.DailySummaryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DailyReportServiceTest {
    @Mock DailyReportRepository reports;
    @Mock DailySummaryRepository summaries;
    @Mock SensorService sensors;
    private DailyReportService service;
    private final LocalDate date = LocalDate.of(2026, 10, 22);

    @BeforeEach void setUp() {
        service = new DailyReportService(reports, summaries, sensors);
    }

    @Test void createsAlertReportFromStoredSummaryWithoutCassandra() {
        SensorDocument sensor = new SensorDocument();
        sensor.setSensorId("SENSOR-001");
        sensor.setName("Salle serveur");
        when(sensors.findDocument("SENSOR-001")).thenReturn(sensor);
        DailySummaryDocument summary = new DailySummaryDocument();
        summary.setSensorId("SENSOR-001");
        summary.setDate("2026-10-22");
        summary.setMeasurementCount(3);
        summary.setEnergyKwh(1.25);
        summary.setAnomalyCount(2);
        when(summaries.findBySensorIdAndDate("SENSOR-001", "2026-10-22"))
                .thenReturn(Optional.of(summary));

        DailyReportResponse result = service.getOrCreate("SENSOR-001", date);

        assertThat(result.sensorId()).isEqualTo("SENSOR-001");
        assertThat(result.sensorName()).isEqualTo("Salle serveur");
        assertThat(result.date()).isEqualTo("2026-10-22");
        assertThat(result.measurementCount()).isEqualTo(3);
        assertThat(result.energyKwh()).isEqualTo(1.25);
        assertThat(result.anomalyCount()).isEqualTo(2);
        assertThat(result.status()).isEqualTo("ALERT");
        ArgumentCaptor<com.smartenergy.storage.mongo.document.DailyReportDocument> saved =
                ArgumentCaptor.forClass(com.smartenergy.storage.mongo.document.DailyReportDocument.class);
        verify(reports).save(saved.capture());
        assertThat(saved.getValue().getId()).isEqualTo("SENSOR-001:2026-10-22");
    }

    @Test void returnsStoredReportWithoutReadingSummary() {
        com.smartenergy.storage.mongo.document.DailyReportDocument report =
                new com.smartenergy.storage.mongo.document.DailyReportDocument();
        report.setId("SENSOR-001:2026-10-22");
        report.setSensorId("SENSOR-001");
        report.setDate("2026-10-22");
        report.setStatus("NORMAL");
        when(reports.findById("SENSOR-001:2026-10-22")).thenReturn(Optional.of(report));

        assertThat(service.getOrCreate("SENSOR-001", date).status()).isEqualTo("NORMAL");
        verifyNoInteractions(summaries, sensors);
        verify(reports, never()).save(any());
    }

    @Test void rejectsMissingSummaryWithoutSavingReport() {
        SensorDocument sensor = new SensorDocument();
        sensor.setSensorId("SENSOR-001");
        when(sensors.findDocument("SENSOR-001")).thenReturn(sensor);
        when(summaries.findBySensorIdAndDate("SENSOR-001", "2026-10-22"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getOrCreate("SENSOR-001", date))
                .isInstanceOf(NoMeasurementsException.class);
        verify(reports, never()).save(any());
    }
}
