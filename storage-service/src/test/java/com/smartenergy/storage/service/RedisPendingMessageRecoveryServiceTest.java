package com.smartenergy.storage.service;

import com.smartenergy.storage.config.RedisStreamProperties;
import com.smartenergy.storage.model.EnergyMeasurementEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Range;
import org.springframework.data.redis.connection.stream.*;
import org.springframework.data.redis.core.StreamOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.List;
import java.util.Map;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RedisPendingMessageRecoveryServiceTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private StreamOperations<String, Object, Object> streamOps;

    @Mock
    private MeasurementPersistenceService persistenceService;

    private RedisStreamProperties properties;
    private RedisPendingMessageRecoveryService recoveryService;

    @BeforeEach
    void setUp() {
        properties = new RedisStreamProperties();
        properties.setName("energy-measurements");
        properties.setGroup("storage-group");
        lenient().when(redisTemplate.opsForStream()).thenReturn(streamOps);
        recoveryService = new RedisPendingMessageRecoveryService(redisTemplate, persistenceService, properties);
    }

    @Test
    @DisplayName("Doit ignorer la reprise si la PEL est vide")
    void shouldDoNothingWhenPelIsEmpty() {
        PendingMessagesSummary summary = new PendingMessagesSummary(
                "storage-group", 0L, Range.unbounded(), Map.of()
        );
        when(streamOps.pending("energy-measurements", "storage-group")).thenReturn(summary);

        int recovered = recoveryService.recoverPendingMessages();

        assertThat(recovered).isZero();
        verify(persistenceService, never()).persist(any());
        verify(streamOps, never()).acknowledge(any(), any(), any(RecordId.class));
    }

    @Test
    @DisplayName("Doit récupérer, persister et acquitter les messages en attente")
    void shouldRecoverAndAcknowledgePendingRecords() {
        PendingMessagesSummary summary = new PendingMessagesSummary(
                "storage-group", 1L, Range.unbounded(), Map.of()
        );
        when(streamOps.pending("energy-measurements", "storage-group")).thenReturn(summary);

        RecordId recordId = RecordId.of("1792500000000-0");
        Map<Object, Object> fields = Map.of(
                "sensorId", "sensor-001",
                "timestamp", "2026-10-21T10:00:00Z",
                "powerKw", "30.0",
                "voltage", "230.0"
        );
        MapRecord<String, Object, Object> record = StreamRecords.newRecord()
                .in("energy-measurements")
                .withId(recordId)
                .ofMap(fields);

        doReturn(List.of(record)).when(streamOps).read(
                any(Consumer.class),
                any(StreamReadOptions.class),
                any(StreamOffset.class)
        );

        when(streamOps.pending(eq("energy-measurements"), eq("storage-group"), any(Range.class), eq(100L)))
                .thenReturn(new PendingMessages("storage-group", List.of()));

        when(streamOps.acknowledge("energy-measurements", "storage-group", recordId)).thenReturn(1L);

        int recovered = recoveryService.recoverPendingMessages();

        assertThat(recovered).isEqualTo(1);
        verify(persistenceService, times(1)).persist(any(EnergyMeasurementEvent.class));
        verify(streamOps, times(1)).acknowledge("energy-measurements", "storage-group", recordId);
    }

    @Test
    void shouldClaimOrphanBeforePersistingAndAcknowledging() {
        when(streamOps.pending("energy-measurements", "storage-group"))
                .thenReturn(new PendingMessagesSummary("storage-group", 1L, Range.unbounded(), Map.of()));
        RecordId id = RecordId.of("1792500000004-0");
        PendingMessage orphan = new PendingMessage(id, Consumer.from("storage-group", "old-consumer"),
                Duration.ofSeconds(10), 1L);
        when(streamOps.pending(eq("energy-measurements"), eq("storage-group"), any(Range.class), eq(100L)))
                .thenReturn(new PendingMessages("storage-group", List.of(orphan)));
        MapRecord<String, Object, Object> record = StreamRecords.newRecord()
                .in("energy-measurements").withId(id)
                .ofMap(Map.of("sensorId", "sensor-001", "timestamp", "2026-10-21T10:00:00Z",
                        "powerKw", "30.0", "voltage", "230.0"));
        when(streamOps.claim("energy-measurements", "storage-group", properties.getConsumerName(),
                Duration.ofSeconds(5), id)).thenReturn(List.of(record));
        when(streamOps.acknowledge("energy-measurements", "storage-group", id)).thenReturn(1L);

        assertThat(recoveryService.recoverPendingMessages()).isEqualTo(1);
        verify(streamOps).claim("energy-measurements", "storage-group", properties.getConsumerName(),
                Duration.ofSeconds(5), id);
        verify(persistenceService).persist(any(EnergyMeasurementEvent.class));
        verify(streamOps).acknowledge("energy-measurements", "storage-group", id);
    }

    @Test
    void shouldKeepMalformedPendingRecordUnacknowledged() {
        when(streamOps.pending("energy-measurements", "storage-group"))
                .thenReturn(new PendingMessagesSummary("storage-group", 1L, Range.unbounded(), Map.of()));
        RecordId id = RecordId.of("1792500000005-0");
        MapRecord<String, Object, Object> record = StreamRecords.newRecord()
                .in("energy-measurements").withId(id)
                .ofMap(Map.of("sensorId", "sensor-001", "powerKw", "30.0", "voltage", "230.0"));
        doReturn(List.of(record)).when(streamOps).read(
                any(Consumer.class), any(StreamReadOptions.class), any(StreamOffset.class));
        when(streamOps.pending(eq("energy-measurements"), eq("storage-group"), any(Range.class), eq(100L)))
                .thenReturn(new PendingMessages("storage-group", List.of()));

        assertThat(recoveryService.recoverPendingMessages()).isZero();
        verifyNoInteractions(persistenceService);
        verify(streamOps, never()).acknowledge(any(), any(), any(RecordId.class));
    }

    @Test
    void shouldKeepPendingRecordWhenPersistenceFails() {
        when(streamOps.pending("energy-measurements", "storage-group"))
                .thenReturn(new PendingMessagesSummary("storage-group", 1L, Range.unbounded(), Map.of()));
        RecordId id = RecordId.of("1792500000006-0");
        MapRecord<String, Object, Object> record = StreamRecords.newRecord()
                .in("energy-measurements").withId(id)
                .ofMap(Map.of("sensorId", "sensor-001", "timestamp", "2026-10-21T10:00:00Z",
                        "powerKw", "30.0", "voltage", "230.0"));
        doReturn(List.of(record)).when(streamOps).read(
                any(Consumer.class), any(StreamReadOptions.class), any(StreamOffset.class));
        when(streamOps.pending(eq("energy-measurements"), eq("storage-group"), any(Range.class), eq(100L)))
                .thenReturn(new PendingMessages("storage-group", List.of()));
        doThrow(new IllegalArgumentException("Cassandra unavailable"))
                .when(persistenceService).persist(any(EnergyMeasurementEvent.class));

        assertThat(recoveryService.recoverPendingMessages()).isZero();
        verify(streamOps, never()).acknowledge(any(), any(), any(RecordId.class));
    }
}
