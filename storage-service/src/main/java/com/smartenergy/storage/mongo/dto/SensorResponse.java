package com.smartenergy.storage.mongo.dto;

import com.smartenergy.storage.mongo.document.SensorDocument;

import java.time.Instant;
import java.util.List;

/**
 * Représentation API d'un capteur.
 */
public record SensorResponse(
        String sensorId,
        String name,
        String building,
        Integer floor,
        double maxPowerKw,
        boolean active,
        List<String> tags,
        double minVoltage,
        double maxVoltage,
        double maxTemperature,
        int samplingIntervalSeconds,
        Instant createdAt,
        Instant updatedAt
) {

    public static SensorResponse from(SensorDocument document) {
        return new SensorResponse(
                document.getSensorId(),
                document.getName(),
                document.getBuilding(),
                document.getFloor(),
                document.getMaxPowerKw(),
                document.isActive(),
                document.getTags() == null ? List.of() : List.copyOf(document.getTags()),
                document.getMinVoltage(),
                document.getMaxVoltage(),
                document.getMaxTemperature(),
                document.getSamplingIntervalSeconds(),
                document.getCreatedAt(),
                document.getUpdatedAt()
        );
    }
}
