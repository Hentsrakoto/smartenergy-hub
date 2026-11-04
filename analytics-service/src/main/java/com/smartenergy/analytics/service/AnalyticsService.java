package com.smartenergy.analytics.service;

import com.smartenergy.analytics.cassandra.entity.MeasurementEntity;
import com.smartenergy.analytics.cassandra.service.CassandraMeasurementService;
import com.smartenergy.analytics.mongo.document.SensorDocument;
import com.smartenergy.analytics.mongo.service.SensorService;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/** Calcul descriptif sur une plage UTC bornée pour un capteur enregistré. */
@Service
public class AnalyticsService {
    private static final Duration MAX_RANGE = Duration.ofDays(31);

    private final CassandraMeasurementService cassandra;
    private final SensorService sensors;

    public AnalyticsService(CassandraMeasurementService cassandra, SensorService sensors) {
        this.cassandra = cassandra;
        this.sensors = sensors;
    }

    public AnalyticsSummary summarize(String sensorId, Instant from, Instant to) {
        validate(from, to);
        SensorDocument sensor = sensors.findDocument(sensorId);
        List<MeasurementEntity> measurements = cassandra.findByRange(sensorId, from, to);
        return MeasurementCalculator.summarize(sensor, measurements, from, to);
    }

    public List<MeasurementAnomaly> findAnomalies(String sensorId, Instant from, Instant to) {
        validate(from, to);
        SensorDocument sensor = sensors.findDocument(sensorId);
        return cassandra.findByRange(sensorId, from, to).stream()
                .map(measurement -> new MeasurementAnomaly(measurement, MeasurementCalculator.reasons(measurement, sensor)))
                .filter(anomaly -> !anomaly.reasons().isEmpty())
                .toList();
    }

    private void validate(Instant from, Instant to) {
        if (from == null || to == null || from.isAfter(to)) {
            throw new IllegalArgumentException("Les bornes 'from' et 'to' sont obligatoires et doivent être ordonnées");
        }
        if (Duration.between(from, to).compareTo(MAX_RANGE) > 0) {
            throw new IllegalArgumentException("La plage ne doit pas dépasser 31 jours");
        }
    }

}
