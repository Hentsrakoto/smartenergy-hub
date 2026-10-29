package com.smartenergy.storage.mongo.dto;

import com.smartenergy.storage.mongo.document.DailyReportDocument;
import java.time.Instant;

public record DailyReportResponse(String sensorId, String sensorName, String date,
                                  long measurementCount, double energyKwh,
                                  long anomalyCount, String status, Instant generatedAt) {
    public static DailyReportResponse from(DailyReportDocument report) {
        return new DailyReportResponse(report.getSensorId(), report.getSensorName(), report.getDate(),
                report.getMeasurementCount(), report.getEnergyKwh(), report.getAnomalyCount(),
                report.getStatus(), report.getGeneratedAt());
    }
}
