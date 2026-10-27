package com.smartenergy.storage.service;

import com.smartenergy.storage.config.RedisStreamProperties;
import com.smartenergy.storage.model.EnergyMeasurementEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.connection.stream.StreamRecords;
import org.springframework.data.redis.core.StreamOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RedisStreamConsumerTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private StreamOperations<String, Object, Object> streamOps;

    @Mock
    private MeasurementPersistenceService persistenceService;

    private RedisStreamProperties properties;
    private RedisStreamConsumer consumer;

    @BeforeEach
    void setUp() {
        properties = new RedisStreamProperties();
        properties.setName("energy-measurements");
        properties.setGroup("storage-group");
        lenient().when(redisTemplate.opsForStream()).thenReturn(streamOps);
        consumer = new RedisStreamConsumer(redisTemplate, persistenceService, properties);
    }

    @Test
    @DisplayName("Doit acquitter (XACK) le message après une persistance réussie")
    void shouldAcknowledgeAfterSuccessfulPersistence() {
        RecordId recordId = RecordId.of("1792500000000-0");
        Map<String, String> fields = Map.of(
                "sensorId", "sensor-001",
                "timestamp", "2026-10-21T10:00:00Z",
                "powerKw", "25.0",
                "voltage", "230.0",
                "temperature", "22.5"
        );
        MapRecord<String, String, String> record = StreamRecords.newRecord()
                .in("energy-measurements")
                .withId(recordId)
                .ofMap(fields);

        when(streamOps.acknowledge("energy-measurements", "storage-group", recordId)).thenReturn(1L);

        boolean processed = consumer.processRecord(record);

        assertThat(processed).isTrue();
        verify(persistenceService, times(1)).persist(any(EnergyMeasurementEvent.class));
        verify(streamOps, times(1)).acknowledge("energy-measurements", "storage-group", recordId);
    }

    @Test
    @DisplayName("NE DOIT PAS acquitter (PAS de XACK) si la persistance échoue (conservation dans la PEL)")
    void shouldNotAcknowledgeIfPersistenceFails() {
        RecordId recordId = RecordId.of("1792500000001-0");
        Map<String, String> fields = Map.of(
                "sensorId", "sensor-001",
                "timestamp", "2026-10-21T10:00:00Z",
                "powerKw", "25.0",
                "voltage", "230.0"
        );
        MapRecord<String, String, String> record = StreamRecords.newRecord()
                .in("energy-measurements")
                .withId(recordId)
                .ofMap(fields);

        doThrow(new RuntimeException("Cassandra connection timed out"))
                .when(persistenceService).persist(any(EnergyMeasurementEvent.class));

        boolean processed = consumer.processRecord(record);

        assertThat(processed).isFalse();
        verify(persistenceService, times(1)).persist(any(EnergyMeasurementEvent.class));
        // Strict verification: NO acknowledge call
        verify(streamOps, never()).acknowledge(eq("energy-measurements"), eq("storage-group"), eq(recordId));
    }

    @Test
    void shouldKeepMalformedMessagePending() {
        RecordId recordId = RecordId.of("1792500000002-0");
        MapRecord<String, String, String> record = StreamRecords.newRecord()
                .in("energy-measurements").withId(recordId)
                .ofMap(Map.of("sensorId", "sensor-001", "powerKw", "25.0", "voltage", "230.0"));

        assertThat(consumer.processRecord(record)).isFalse();
        verifyNoInteractions(persistenceService);
        verify(streamOps, never()).acknowledge(any(), any(), any(RecordId.class));
    }

    @Test
    void shouldKeepPersistenceValidationErrorPending() {
        RecordId recordId = RecordId.of("1792500000003-0");
        MapRecord<String, String, String> record = StreamRecords.newRecord()
                .in("energy-measurements").withId(recordId)
                .ofMap(Map.of("sensorId", "sensor-001", "timestamp", "2026-10-21T10:00:00Z",
                        "powerKw", "25.0", "voltage", "230.0"));
        doThrow(new IllegalArgumentException("persistence rejected"))
                .when(persistenceService).persist(any(EnergyMeasurementEvent.class));

        assertThat(consumer.processRecord(record)).isFalse();
        verify(streamOps, never()).acknowledge(any(), any(), any(RecordId.class));
    }
}
