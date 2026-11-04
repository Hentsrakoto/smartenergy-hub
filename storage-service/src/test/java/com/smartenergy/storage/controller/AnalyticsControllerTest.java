package com.smartenergy.storage.controller;

import com.smartenergy.storage.service.AnalyticsService;
import com.smartenergy.storage.service.AnalyticsSummary;
import com.smartenergy.storage.service.MeasurementAnomaly;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AnalyticsController.class)
@ContextConfiguration(classes = {AnalyticsController.class, com.smartenergy.storage.exception.ApiExceptionHandler.class})
class AnalyticsControllerTest {
    @Autowired MockMvc mvc;
    @MockBean AnalyticsService service;

    @Test
    void exposesSummaryAndAnomalyRoutes() throws Exception {
        Instant from = Instant.parse("2026-10-28T00:00:00Z");
        Instant to = Instant.parse("2026-10-28T00:01:00Z");
        when(service.summarize("sensor-001", from, to))
                .thenReturn(new AnalyticsSummary("sensor-001", from, to, 2, 1, 2, 3, 0.066667, 1));
        when(service.findAnomalies("sensor-001", from, to)).thenReturn(List.<MeasurementAnomaly>of());

        mvc.perform(get("/api/analytics/sensor-001/summary")
                        .param("from", from.toString()).param("to", to.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.energyKwh").value(0.066667));
        mvc.perform(get("/api/analytics/sensor-001/anomalies")
                        .param("from", from.toString()).param("to", to.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }
    @Test
    void analyticsFailureReturnsExplicitSanitized503() throws Exception {
        var from=Instant.parse("2026-10-28T00:00:00Z");
        when(service.summarize("sensor-001", from, from))
                .thenThrow(new com.smartenergy.storage.client.AnalyticsHttpException(503, null));
        mvc.perform(get("/api/analytics/sensor-001/summary")
                .param("from", from.toString()).param("to", from.toString()))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.message").value("Service Analytics indisponible"));
    }

    @Test
    void analyticsClientErrorsKeepUpstreamStatusAndJson() throws Exception {
        var from=Instant.parse("2026-10-28T00:00:00Z");
        for (int statusCode : new int[]{400,404}) {
            var body=new com.fasterxml.jackson.databind.ObjectMapper().readTree("{\"code\":\"UPSTREAM\",\"message\":\"Erreur métier\"}");
            org.mockito.Mockito.doThrow(new com.smartenergy.storage.client.AnalyticsHttpException(statusCode, body))
                    .when(service).summarize("sensor-001", from, from);
            mvc.perform(get("/api/analytics/sensor-001/summary")
                    .param("from", from.toString()).param("to", from.toString()))
                    .andExpect(status().is(statusCode))
                    .andExpect(jsonPath("$.code").value("UPSTREAM"))
                    .andExpect(jsonPath("$.message").value("Erreur métier"));
        }
    }

}
