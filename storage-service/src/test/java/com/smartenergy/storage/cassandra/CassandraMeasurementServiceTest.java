package com.smartenergy.storage.cassandra;

import com.smartenergy.storage.cassandra.entity.MeasurementEntity;
import com.smartenergy.storage.cassandra.repository.MeasurementRepository;
import com.smartenergy.storage.cassandra.service.CassandraMeasurementService;
import com.smartenergy.storage.model.EnergyMeasurementEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CassandraMeasurementServiceTest {

    @Mock
    private MeasurementRepository repository;

    private CassandraMeasurementService service;

    @BeforeEach
    void setUp() {
        service = new CassandraMeasurementService(repository);
    }

    @Test
    @DisplayName("save() doit créer une entité avec la bonne clé de partition (sensor_id, day)")
    void shouldPersistWithCorrectPartitionKey() {
        Instant ts = Instant.parse("2026-10-21T14:30:00Z");
        EnergyMeasurementEvent event = new EnergyMeasurementEvent(
                "1-0", "sensor-001", ts, 42.5, 230.1, 25.0);

        MeasurementEntity returned = new MeasurementEntity(
                "sensor-001", LocalDate.of(2026, 10, 21), ts, 42.5, 230.1, 25.0);
        when(repository.save(any(MeasurementEntity.class))).thenReturn(returned);

        MeasurementEntity result = service.save(event);

        ArgumentCaptor<MeasurementEntity> captor = ArgumentCaptor.forClass(MeasurementEntity.class);
        verify(repository).save(captor.capture());
        MeasurementEntity captured = captor.getValue();

        assertThat(captured.getSensorId()).isEqualTo("sensor-001");
        assertThat(captured.getDay()).isEqualTo(LocalDate.of(2026, 10, 21));
        assertThat(captured.getMeasuredAt()).isEqualTo(ts);
        assertThat(captured.getPowerKw()).isEqualTo(42.5);
        assertThat(captured.getVoltage()).isEqualTo(230.1);
        assertThat(captured.getTemperature()).isEqualTo(25.0);
        assertThat(result).isEqualTo(returned);
    }

    @Test
    @DisplayName("save() doit calculer le jour correct pour un timestamp UTC minuit")
    void shouldComputeDayFromUtcTimestamp() {
        // 2026-10-21T23:59:59Z -> day = 2026-10-21
        Instant ts = Instant.parse("2026-10-21T23:59:59Z");
        EnergyMeasurementEvent event = new EnergyMeasurementEvent(
                "2-0", "sensor-002", ts, 10.0, 220.0, null);
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.save(event);

        ArgumentCaptor<MeasurementEntity> captor = ArgumentCaptor.forClass(MeasurementEntity.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getDay()).isEqualTo(LocalDate.of(2026, 10, 21));
    }

    @Test
    @DisplayName("findByRange() doit émettre une requête par jour lorsque la plage couvre plusieurs jours")
    void shouldQueryPerDayForMultiDayRange() {
        Instant from = Instant.parse("2026-10-20T22:00:00Z");
        Instant to   = Instant.parse("2026-10-21T02:00:00Z");

        when(repository.findBySensorIdAndDayAndMeasuredAtBetween(any(), any(), any(), any()))
                .thenReturn(List.of());

        service.findByRange("sensor-001", from, to);

        // 2 jours couverts : 2026-10-20 et 2026-10-21
        verify(repository, times(2))
                .findBySensorIdAndDayAndMeasuredAtBetween(any(), any(), any(), any());
    }

    @Test
    @DisplayName("findByRange() doit refuser une plage inversée")
    void shouldRejectInvertedRange() {
        Instant from = Instant.parse("2026-10-21T02:00:00Z");
        Instant to   = Instant.parse("2026-10-20T22:00:00Z");

        assertThatThrownBy(() -> service.findByRange("sensor-001", from, to))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("'from' doit être antérieur ou égal à 'to'");

        verifyNoInteractions(repository);
    }

    @Test
    @DisplayName("findByRange() doit retourner un ordre global décroissant sur plusieurs jours")
    void shouldSortResultsDescendingAcrossDays() {
        Instant from = Instant.parse("2026-10-20T00:00:00Z");
        Instant to   = Instant.parse("2026-10-21T23:59:59Z");
        Instant day1Latest = Instant.parse("2026-10-20T23:00:00Z");
        Instant day2Latest = Instant.parse("2026-10-21T12:00:00Z");

        MeasurementEntity day1 = new MeasurementEntity(
                "sensor-001", LocalDate.of(2026, 10, 20), day1Latest, 10.0, 230.0, 20.0);
        MeasurementEntity day2 = new MeasurementEntity(
                "sensor-001", LocalDate.of(2026, 10, 21), day2Latest, 20.0, 231.0, 21.0);

        when(repository.findBySensorIdAndDayAndMeasuredAtBetween(
                "sensor-001", LocalDate.of(2026, 10, 20), from, to))
                .thenReturn(List.of(day1));
        when(repository.findBySensorIdAndDayAndMeasuredAtBetween(
                "sensor-001", LocalDate.of(2026, 10, 21), from, to))
                .thenReturn(List.of(day2));

        List<MeasurementEntity> result = service.findByRange("sensor-001", from, to);

        assertThat(result).containsExactly(day2, day1);
    }

    @Test
    @DisplayName("findLatest() doit déléguer au repository avec les bons paramètres")
    void shouldFindLatestByDayAndSensorId() {
        LocalDate day = LocalDate.of(2026, 10, 21);
        MeasurementEntity entity = new MeasurementEntity(
                "sensor-001", day, Instant.now(), 55.0, 231.0, 30.0);
        when(repository.findLatestBySensorIdAndDay("sensor-001", day))
                .thenReturn(Optional.of(entity));

        Optional<MeasurementEntity> result = service.findLatest("sensor-001", day);

        assertThat(result).isPresent();
        assertThat(result.get().getSensorId()).isEqualTo("sensor-001");
    }

    @Test
    @DisplayName("findLatest() doit retourner Optional vide si aucune mesure pour ce jour")
    void shouldReturnEmptyOptionalWhenNoMeasurements() {
        LocalDate day = LocalDate.of(2026, 10, 21);
        when(repository.findLatestBySensorIdAndDay(any(), any())).thenReturn(Optional.empty());

        Optional<MeasurementEntity> result = service.findLatest("sensor-999", day);

        assertThat(result).isEmpty();
    }
}
