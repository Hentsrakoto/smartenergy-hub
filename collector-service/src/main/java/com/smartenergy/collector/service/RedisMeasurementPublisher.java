package com.smartenergy.collector.service;

import com.smartenergy.collector.dto.EnergyMeasurementRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.connection.stream.StreamRecords;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Implémentation du service de publication vers Redis Streams.
 */
@Service
public class RedisMeasurementPublisher implements MeasurementPublisher {

    private static final Logger log = LoggerFactory.getLogger(RedisMeasurementPublisher.class);

    private final StringRedisTemplate redisTemplate;
    private final String streamName;

    public RedisMeasurementPublisher(
            StringRedisTemplate redisTemplate,
            @Value("${smartenergy.stream.name:energy-measurements}") String streamName) {
        this.redisTemplate = redisTemplate;
        this.streamName = streamName;
    }

    @Override
    public RecordId publish(EnergyMeasurementRequest request) {
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("sensorId", request.sensorId());
        fields.put("timestamp", request.timestamp().toString());
        fields.put("powerKw", String.valueOf(request.powerKw()));
        fields.put("voltage", String.valueOf(request.voltage()));
        if (request.temperature() != null) {
            fields.put("temperature", String.valueOf(request.temperature()));
        }

        RecordId recordId = redisTemplate.opsForStream()
                .add(StreamRecords.newRecord().in(streamName).ofMap(fields));

        log.info("Mesure du capteur '{}' publiée avec succès dans le stream '{}' (Message ID: '{}')",
                request.sensorId(), streamName, recordId);

        return recordId;
    }
}
