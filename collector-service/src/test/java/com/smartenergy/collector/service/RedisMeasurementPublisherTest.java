package com.smartenergy.collector.service;

import com.smartenergy.collector.dto.EnergyMeasurementRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.core.StreamOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RedisMeasurementPublisherTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private StreamOperations<String, Object, Object> streamOperations;

    @Test
    @DisplayName("publish() doit formater les champs et ajouter un enregistrement dans le stream Redis")
    @SuppressWarnings("unchecked")
    void testPublishFormatsAndSendsToStream() {
        when(redisTemplate.opsForStream()).thenReturn((StreamOperations) streamOperations);
        RecordId expectedId = RecordId.of("1792507200000-0");
        when(streamOperations.add(any(MapRecord.class))).thenReturn(expectedId);

        RedisMeasurementPublisher publisher = new RedisMeasurementPublisher(redisTemplate, "energy-measurements");

        EnergyMeasurementRequest request = new EnergyMeasurementRequest(
                "sensor-42",
                Instant.parse("2026-10-20T15:00:00Z"),
                18.75,
                231.2,
                24.5
        );

        RecordId actualId = publisher.publish(request);

        assertThat(actualId).isEqualTo(expectedId);

        ArgumentCaptor<MapRecord<String, String, String>> captor = ArgumentCaptor.forClass(MapRecord.class);
        verify(streamOperations).add((MapRecord) captor.capture());

        MapRecord<String, String, String> record = captor.getValue();
        assertThat(record.getStream()).isEqualTo("energy-measurements");

        Map<String, String> value = record.getValue();
        assertThat(value.get("sensorId")).isEqualTo("sensor-42");
        assertThat(value.get("timestamp")).isEqualTo("2026-10-20T15:00:00Z");
        assertThat(value.get("powerKw")).isEqualTo("18.75");
        assertThat(value.get("voltage")).isEqualTo("231.2");
        assertThat(value.get("temperature")).isEqualTo("24.5");
    }
}
