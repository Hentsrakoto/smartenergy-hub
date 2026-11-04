package com.smartenergy.storage.mongo.service;

import com.smartenergy.storage.client.AnalyticsClient;
import com.smartenergy.storage.mongo.dto.DailyReportResponse;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.Map;

/** Façade de compatibilité vers les rapports possédés par Analytics. */
@Service
public class DailyReportService {
    private final AnalyticsClient client;

    public DailyReportService(AnalyticsClient client) {
        this.client = client;
    }

    public DailyReportResponse getOrCreate(String id, LocalDate date) {
        if (date == null) throw new IllegalArgumentException("date est obligatoire");
        return client.get("/api/sensors/" + id + "/daily-report",
                Map.of("date", date), DailyReportResponse.class);
    }
}
