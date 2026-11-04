package com.smartenergy.analytics.service;
import com.smartenergy.analytics.cassandra.entity.MeasurementEntity;
import com.smartenergy.analytics.mongo.document.SensorDocument;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
/** Shared period/day formulas; inputs are already selected for the UTC window. */
public final class MeasurementCalculator {
    private MeasurementCalculator() {}
    public static AnalyticsSummary summarize(SensorDocument sensor, List<MeasurementEntity> measurements, Instant from, Instant to) {
        String sensorId = sensor.getSensorId();
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
    public static List<String> reasons(MeasurementEntity measurement, SensorDocument sensor) {
        List<String> result = new ArrayList<>();
        if (measurement.getPowerKw() > sensor.getMaxPowerKw()) result.add("POWER_ABOVE_MAX");
        if (measurement.getVoltage() < sensor.getMinVoltage()) result.add("VOLTAGE_BELOW_MIN");
        if (measurement.getVoltage() > sensor.getMaxVoltage()) result.add("VOLTAGE_ABOVE_MAX");
        if (measurement.getTemperature() != null && measurement.getTemperature() > sensor.getMaxTemperature()) {
            result.add("TEMPERATURE_ABOVE_MAX");
        }
        return result;
    }

    private static double round(double value) {
        return BigDecimal.valueOf(value).setScale(6, RoundingMode.HALF_UP).doubleValue();
    }
}
