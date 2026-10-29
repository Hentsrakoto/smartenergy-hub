package com.smartenergy.storage.mongo.service;

import com.smartenergy.storage.mongo.document.DailyReportDocument;
import com.smartenergy.storage.mongo.document.DailySummaryDocument;
import com.smartenergy.storage.mongo.document.SensorDocument;
import com.smartenergy.storage.mongo.dto.DailyReportResponse;
import com.smartenergy.storage.mongo.exception.NoMeasurementsException;
import com.smartenergy.storage.mongo.repository.DailyReportRepository;
import com.smartenergy.storage.mongo.repository.DailySummaryRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;

@Service
public class DailyReportService {
    private final DailyReportRepository reports;
    private final DailySummaryRepository summaries;
    private final SensorService sensors;

    public DailyReportService(DailyReportRepository reports, DailySummaryRepository summaries,
                              SensorService sensors) {
        this.reports = reports;
        this.summaries = summaries;
        this.sensors = sensors;
    }

    /** Reads a saved snapshot, or creates one using MongoDB data only. */
    public DailyReportResponse getOrCreate(String sensorId, LocalDate date) {
        if (date == null) {
            throw new IllegalArgumentException("date est obligatoire");
        }
        if (sensorId == null || !sensorId.matches("^[a-zA-Z0-9_-]{3,50}$")) {
            throw new IllegalArgumentException("sensorId doit comporter entre 3 et 50 caractères alphanumériques, tirets ou underscores");
        }
        String id = sensorId + ":" + date;
        return reports.findById(id).map(DailyReportResponse::from).orElseGet(() -> {
            SensorDocument sensor = sensors.findDocument(sensorId);
            DailySummaryDocument summary = summaries.findBySensorIdAndDate(sensor.getSensorId(), date.toString())
                    .orElseThrow(() -> new NoMeasurementsException(sensor.getSensorId(), date.toString()));
            DailyReportDocument report = new DailyReportDocument();
            report.setId(id);
            report.setSensorId(sensor.getSensorId());
            report.setSensorName(sensor.getName());
            report.setDate(date.toString());
            report.setMeasurementCount(summary.getMeasurementCount());
            report.setEnergyKwh(summary.getEnergyKwh());
            report.setAnomalyCount(summary.getAnomalyCount());
            report.setStatus(summary.getAnomalyCount() > 0 ? "ALERT" : "NORMAL");
            report.setGeneratedAt(Instant.now());
            reports.save(report);
            return DailyReportResponse.from(report);
        });
    }
}
