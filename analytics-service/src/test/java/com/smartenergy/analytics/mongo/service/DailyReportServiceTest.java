package com.smartenergy.analytics.mongo.service;

import com.smartenergy.analytics.mongo.document.DailySummaryDocument;
import com.smartenergy.analytics.mongo.document.SensorDocument;
import com.smartenergy.analytics.mongo.dto.DailyReportResponse;
import com.smartenergy.analytics.mongo.exception.NoMeasurementsException;
import com.smartenergy.analytics.mongo.repository.DailyReportRepository;
import com.smartenergy.analytics.mongo.repository.DailySummaryRepository;
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
        assertThat(result.generatedAt()).isEqualTo(java.util.Date.from(result.generatedAt()).toInstant());
        ArgumentCaptor<com.smartenergy.analytics.mongo.document.DailyReportDocument> saved =
                ArgumentCaptor.forClass(com.smartenergy.analytics.mongo.document.DailyReportDocument.class);
        verify(reports).insert(saved.capture());
        assertThat(saved.getValue().getId()).isEqualTo("SENSOR-001:2026-10-22");
    }

    @Test void returnsStoredReportWithoutReadingSummary() {
        com.smartenergy.analytics.mongo.document.DailyReportDocument report =
                new com.smartenergy.analytics.mongo.document.DailyReportDocument();
        report.setId("SENSOR-001:2026-10-22");
        report.setSensorId("SENSOR-001");
        report.setDate("2026-10-22");
        report.setStatus("NORMAL");
        when(reports.findById("SENSOR-001:2026-10-22")).thenReturn(Optional.of(report));

        assertThat(service.getOrCreate("SENSOR-001", date).status()).isEqualTo("NORMAL");
        verifyNoInteractions(summaries, sensors);
        verify(reports, never()).insert(any(com.smartenergy.analytics.mongo.document.DailyReportDocument.class));
    }

    @Test void rejectsMissingSummaryWithoutSavingReport() {
        SensorDocument sensor = new SensorDocument();
        sensor.setSensorId("SENSOR-001");
        when(sensors.findDocument("SENSOR-001")).thenReturn(sensor);
        when(summaries.findBySensorIdAndDate("SENSOR-001", "2026-10-22"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getOrCreate("SENSOR-001", date))
                .isInstanceOf(NoMeasurementsException.class);
        verify(reports, never()).insert(any(com.smartenergy.analytics.mongo.document.DailyReportDocument.class));
    }
    @Test void concurrentFirstGetsReturnTheAtomicWinnerDespiteChangedSummary() throws Exception {
        var winner = new java.util.concurrent.atomic.AtomicReference<com.smartenergy.analytics.mongo.document.DailyReportDocument>();
        var candidates = new java.util.concurrent.CyclicBarrier(2);
        var versions = new java.util.concurrent.atomic.AtomicInteger();
        SensorDocument sensor = new SensorDocument();
        sensor.setSensorId("SENSOR-001");
        when(sensors.findDocument("SENSOR-001")).thenReturn(sensor);
        when(reports.findById("SENSOR-001:2026-10-22")).thenAnswer(call -> Optional.ofNullable(winner.get()));
        when(summaries.findBySensorIdAndDate("SENSOR-001", "2026-10-22")).thenAnswer(call -> {
            DailySummaryDocument summary = new DailySummaryDocument();
            int version = versions.incrementAndGet();
            summary.setMeasurementCount(version);
            summary.setEnergyKwh(version);
            return Optional.of(summary);
        });
        when(reports.insert(any(com.smartenergy.analytics.mongo.document.DailyReportDocument.class))).thenAnswer(call -> {
            com.smartenergy.analytics.mongo.document.DailyReportDocument candidate = call.getArgument(0);
            candidates.await(3, java.util.concurrent.TimeUnit.SECONDS);
            if (!winner.compareAndSet(null, candidate)) throw new org.springframework.dao.DuplicateKeyException("existing snapshot");
            return candidate;
        });
        lenient().doThrow(new AssertionError("A snapshot must never be overwritten with save"))
                .when(reports).save(any(com.smartenergy.analytics.mongo.document.DailyReportDocument.class));
        var pool = java.util.concurrent.Executors.newFixedThreadPool(2);
        try {
            var first = pool.submit(() -> service.getOrCreate("SENSOR-001", date));
            var second = pool.submit(() -> service.getOrCreate("SENSOR-001", date));
            var expected = DailyReportResponse.from(winnerAfter(first, second));
            assertThat(first.get()).isEqualTo(expected);
            assertThat(second.get()).isEqualTo(expected);
            versions.set(999);
            assertThat(service.getOrCreate("SENSOR-001", date)).isEqualTo(expected);
            verify(summaries, times(2)).findBySensorIdAndDate("SENSOR-001", "2026-10-22");
            verify(reports, never()).save(any(com.smartenergy.analytics.mongo.document.DailyReportDocument.class));
        } finally { pool.shutdownNow(); }
    }

    private com.smartenergy.analytics.mongo.document.DailyReportDocument winnerAfter(
            java.util.concurrent.Future<DailyReportResponse> first,
            java.util.concurrent.Future<DailyReportResponse> second) throws Exception {
        first.get(4, java.util.concurrent.TimeUnit.SECONDS);
        second.get(4, java.util.concurrent.TimeUnit.SECONDS);
        return reports.findById("SENSOR-001:2026-10-22").orElseThrow();
    }
}
