package com.smartenergy.storage.controller;

import com.smartenergy.storage.mongo.dto.DailyReportResponse;
import com.smartenergy.storage.mongo.exception.NoMeasurementsException;
import com.smartenergy.storage.mongo.service.DailyReportService;
import com.smartenergy.storage.StorageApplication;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.context.ContextConfiguration;

import java.time.Instant;
import java.time.LocalDate;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(DailyReportController.class)
@ContextConfiguration(classes = StorageApplication.class)
class DailyReportControllerTest {
    @Autowired MockMvc mvc;
    @MockBean DailyReportService reports;

    @Test void returnsDailyReportFromService() throws Exception {
        when(reports.getOrCreate("SENSOR-001", LocalDate.of(2026, 10, 22)))
                .thenReturn(new DailyReportResponse("SENSOR-001", "Salle serveur", "2026-10-22",
                        3, 1.25, 2, "ALERT", Instant.parse("2026-10-22T12:00:00Z")));

        mvc.perform(get("/api/sensors/SENSOR-001/daily-report").param("date", "2026-10-22"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sensorName").value("Salle serveur"))
                .andExpect(jsonPath("$.energyKwh").value(1.25))
                .andExpect(jsonPath("$.status").value("ALERT"));
    }

    @Test void rejectsInvalidDate() throws Exception {
        mvc.perform(get("/api/sensors/SENSOR-001/daily-report").param("date", "2026-13-40"))
                .andExpect(status().isBadRequest());
    }

    @Test void returnsNotFoundWithoutStoredSummary() throws Exception {
        when(reports.getOrCreate("SENSOR-001", LocalDate.of(2026, 10, 22)))
                .thenThrow(new NoMeasurementsException("SENSOR-001", "2026-10-22"));
        mvc.perform(get("/api/sensors/SENSOR-001/daily-report").param("date", "2026-10-22"))
                .andExpect(status().isNotFound());
    }
}
