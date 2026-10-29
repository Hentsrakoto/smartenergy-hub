package com.smartenergy.storage.service;

import com.smartenergy.storage.cassandra.entity.MeasurementEntity;
import com.smartenergy.storage.cassandra.service.CassandraMeasurementService;
import com.smartenergy.storage.mongo.document.DailySummaryDocument;
import com.smartenergy.storage.mongo.repository.DailySummaryRepository;
import com.smartenergy.storage.mongo.service.SensorService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LatestMeasurementServiceTest {

    @Mock DailySummaryRepository summaries;
    @Mock CassandraMeasurementService cassandra;
    @Mock SensorService sensors;

    @Test
    void latestUsesMostRecentIndexedDayEvenWhenOlderEventsArriveLater() {
        DailySummaryDocument latestDay = new DailySummaryDocument();
        latestDay.setSensorId("sensor-001");
        latestDay.setDate("2026-10-28");
        MeasurementEntity expected = new MeasurementEntity("sensor-001", LocalDate.of(2026, 10, 28),
                Instant.parse("2026-10-28T09:00:00Z"), 4.0, 230.0, 25.0);
        when(summaries.findFirstBySensorIdOrderByDateDesc("sensor-001"))
                .thenReturn(Optional.of(latestDay));
        when(cassandra.findLatest("sensor-001", LocalDate.of(2026, 10, 28)))
                .thenReturn(Optional.of(expected));

        Optional<MeasurementEntity> result = new LatestMeasurementService(summaries, cassandra, sensors)
                .findLatestKnown("sensor-001");

        assertThat(result).contains(expected);
        verify(sensors).findDocument("sensor-001");
    }

    @Test
    void returnsEmptyWhenRegisteredSensorHasNoIndexedMeasurements() {
        when(summaries.findFirstBySensorIdOrderByDateDesc("sensor-001"))
                .thenReturn(Optional.empty());

        Optional<MeasurementEntity> result = new LatestMeasurementService(summaries, cassandra, sensors)
                .findLatestKnown("sensor-001");

        assertThat(result).isEmpty();
    }
}
