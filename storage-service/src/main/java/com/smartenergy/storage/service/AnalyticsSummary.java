package com.smartenergy.storage.service;

import java.time.Instant;

public record AnalyticsSummary(String sensorId, Instant from, Instant to, long measurementCount,
                               double minPowerKw, double avgPowerKw, double maxPowerKw,
                               double energyKwh, long anomalyCount) {
}
