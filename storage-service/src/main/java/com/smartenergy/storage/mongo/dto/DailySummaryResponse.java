package com.smartenergy.storage.mongo.dto;

import com.smartenergy.storage.mongo.document.DailySummaryDocument;

import java.time.Instant;

/**
 * Représentation API d'un agrégat quotidien.
 *
 * <p>Les champs du guide ({@code sensorId}, {@code date}, {@code avgPowerKw},
 * {@code maxPowerKw}, {@code energyKwh}, {@code anomalyCount}) sont complétés par
 * {@code measurementCount}, {@code minPowerKw} et {@code updatedAt}.</p>
 */
public record DailySummaryResponse(
        String sensorId,
        String date,
        double avgPowerKw,
        double maxPowerKw,
        double energyKwh,
        long anomalyCount,
        long measurementCount,
        double minPowerKw,
        Instant updatedAt
) {

    public static DailySummaryResponse from(DailySummaryDocument document) {
        return new DailySummaryResponse(
                document.getSensorId(),
                document.getDate(),
                document.getAvgPowerKw(),
                document.getMaxPowerKw(),
                document.getEnergyKwh(),
                document.getAnomalyCount(),
                document.getMeasurementCount(),
                document.getMinPowerKw(),
                document.getUpdatedAt()
        );
    }
}
