package com.smartenergy.storage.service;

import com.smartenergy.storage.config.RedisStreamProperties;
import com.smartenergy.storage.model.EnergyMeasurementEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.stream.StreamListener;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class RedisStreamConsumer implements StreamListener<String, MapRecord<String, String, String>> {

    private static final Logger log = LoggerFactory.getLogger(RedisStreamConsumer.class);

    private final StringRedisTemplate redisTemplate;
    private final MeasurementPersistenceService persistenceService;
    private final RedisStreamProperties properties;

    public RedisStreamConsumer(
            StringRedisTemplate redisTemplate,
            MeasurementPersistenceService persistenceService,
            RedisStreamProperties properties) {
        this.redisTemplate = redisTemplate;
        this.persistenceService = persistenceService;
        this.properties = properties;
    }

    @Override
    public void onMessage(MapRecord<String, String, String> record) {
        processRecord(record);
    }

    public boolean processRecord(MapRecord<String, ?, ?> record) {
        String recordId = record.getId().getValue();
        Map<?, ?> fields = record.getValue();

        log.debug("Réception message Redis Streams: id='{}', stream='{}'", recordId, record.getStream());

        EnergyMeasurementEvent event;
        try {
            event = EnergyMeasurementEvent.fromMap(recordId, (Map<Object, Object>) fields);
        } catch (RuntimeException e) {
            log.error("Message Redis '{}' illisible/invalide: {}. NON acquitté, conservé dans la PEL pour inspection.",
                    recordId, e.getMessage());
            return false;
        }

        try {
            // 1. Tenter la persistance
            persistenceService.persist(event);

            // 2. Acquittement (XACK) UNIQUEMENT après persistance réussie
            Long ackCount = redisTemplate.opsForStream().acknowledge(
                    properties.getName(),
                    properties.getGroup(),
                    record.getId()
            );

            log.info("Message '{}' persisté et acquitté avec succès (XACK: {}) pour le capteur '{}'",
                    recordId, ackCount, event.sensorId());
            return true;

        } catch (Exception e) {
            log.error("Échec persistance ou acquittement pour le message '{}'. Conservé/rejouable dans la PEL: {}",
                    recordId, e.getMessage());
            // Aucun XACK: le message reste en attente dans la PEL (Pending Entries List)
            return false;
        }
    }
}
