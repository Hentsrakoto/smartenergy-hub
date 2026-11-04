package com.smartenergy.storage.service;

import com.smartenergy.storage.client.AnalyticsClient;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/** Façade des anciennes routes analytiques ; les calculs appartiennent à Analytics. */
@Service
public class AnalyticsService {
    private final AnalyticsClient client;

    public AnalyticsService(AnalyticsClient client) {
        this.client = client;
    }

    public AnalyticsSummary summarize(String id, Instant from, Instant to) {
        return client.get("/api/analytics/" + id + "/summary",
                Map.of("from", from, "to", to), AnalyticsSummary.class);
    }

    public List<MeasurementAnomaly> findAnomalies(String id, Instant from, Instant to) {
        return Arrays.asList(client.get("/api/analytics/" + id + "/anomalies",
                Map.of("from", from, "to", to), MeasurementAnomaly[].class));
    }
}
