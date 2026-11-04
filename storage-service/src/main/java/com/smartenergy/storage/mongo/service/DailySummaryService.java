package com.smartenergy.storage.mongo.service;

import com.smartenergy.storage.client.AnalyticsClient;
import com.smartenergy.storage.mongo.document.DailySummaryDocument;
import com.smartenergy.storage.mongo.dto.DailySummaryResponse;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.Map;

/** Façade de compatibilité ; Analytics possède les calculs et écritures des résumés. */
@Service
public class DailySummaryService {
    private final AnalyticsClient client;

    public DailySummaryService(AnalyticsClient client) {
        this.client = client;
    }

    public DailySummaryResponse getOrCompute(String id, LocalDate date, boolean refresh) {
        requireDate(date);
        return client.get("/api/sensors/" + id + "/daily-summary",
                Map.of("date", date, "refresh", refresh), DailySummaryResponse.class);
    }

    /** Le nom historique est conservé pour CassandraPersistenceService ; aucune écriture locale. */
    public DailySummaryDocument computeAndStore(String id, LocalDate date) {
        requireDate(date);
        DailySummaryResponse response = client.recompute(id, date.toString(), DailySummaryResponse.class);
        DailySummaryDocument document = new DailySummaryDocument();
        document.setId(response.sensorId() + ":" + response.date());
        document.setSensorId(response.sensorId());
        document.setDate(response.date());
        document.setMeasurementCount(response.measurementCount());
        document.setMinPowerKw(response.minPowerKw());
        document.setAvgPowerKw(response.avgPowerKw());
        document.setMaxPowerKw(response.maxPowerKw());
        document.setEnergyKwh(response.energyKwh());
        document.setAnomalyCount(response.anomalyCount());
        document.setUpdatedAt(response.updatedAt());
        return document;
    }

    private void requireDate(LocalDate date) {
        if (date == null) throw new IllegalArgumentException("date est obligatoire");
    }
}
