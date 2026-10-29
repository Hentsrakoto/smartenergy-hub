package com.smartenergy.storage.service;

import com.smartenergy.storage.cassandra.entity.MeasurementEntity;
import com.smartenergy.storage.cassandra.service.CassandraMeasurementService;
import com.smartenergy.storage.mongo.document.SensorDocument;
import com.smartenergy.storage.mongo.service.SensorService;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
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
        if (measurements.isEmpty()) {
            return new AnalyticsSummary(sensorId, from, to, 0, 0, 0, 0, 0, 0);
        }

        List<MeasurementEntity> sorted = new ArrayList<>(measurements);
        sorted.sort(Comparator.comparing(MeasurementEntity::getMeasuredAt));
        double total = 0;
        double min = Double.POSITIVE_INFINITY;
        double max = Double.NEGATIVE_INFINITY;
        double energy = 0;
        long anomalies = 0;
        for (int i = 0; i < sorted.size(); i++) {
            MeasurementEntity measurement = sorted.get(i);
            double power = measurement.getPowerKw();
            total += power;
            min = Math.min(min, power);
            max = Math.max(max, power);
            if (!reasons(measurement, sensor).isEmpty()) {
                anomalies++;
            }
            Instant end = i + 1 < sorted.size()
                    ? sorted.get(i + 1).getMeasuredAt()
                    : measurement.getMeasuredAt().plusSeconds(Math.max(1, sensor.getSamplingIntervalSeconds()));
            if (end.isAfter(to)) end = to;
            long millis = Duration.between(measurement.getMeasuredAt(), end).toMillis();
            if (millis > 0) {
                energy += power * Math.min(millis / 3_600_000.0, 24.0);
            }
        }
        return new AnalyticsSummary(sensorId, from, to, sorted.size(), round(min),
                round(total / sorted.size()), round(max), round(energy), anomalies);
    }

    public List<MeasurementAnomaly> findAnomalies(String sensorId, Instant from, Instant to) {
        validate(from, to);
        SensorDocument sensor = sensors.findDocument(sensorId);
        return cassandra.findByRange(sensorId, from, to).stream()
                .map(measurement -> new MeasurementAnomaly(measurement, reasons(measurement, sensor)))
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

    private List<String> reasons(MeasurementEntity measurement, SensorDocument sensor) {
        List<String> result = new ArrayList<>();
        if (measurement.getPowerKw() > sensor.getMaxPowerKw()) result.add("POWER_ABOVE_MAX");
        if (measurement.getVoltage() < sensor.getMinVoltage()) result.add("VOLTAGE_BELOW_MIN");
        if (measurement.getVoltage() > sensor.getMaxVoltage()) result.add("VOLTAGE_ABOVE_MAX");
        if (measurement.getTemperature() != null && measurement.getTemperature() > sensor.getMaxTemperature()) {
            result.add("TEMPERATURE_ABOVE_MAX");
        }
        return result;
    }

    private double round(double value) {
        return BigDecimal.valueOf(value).setScale(6, RoundingMode.HALF_UP).doubleValue();
    }
}
