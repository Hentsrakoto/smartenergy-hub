package com.smartenergy.analytics.controller;

import com.smartenergy.analytics.mongo.service.DailySummaryService;
import com.smartenergy.analytics.mongo.exception.SensorNotFoundException;
import com.smartenergy.analytics.mongo.exception.NoMeasurementsException;
import com.smartenergy.analytics.exception.ApiExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import java.time.LocalDate;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(DailySummaryController.class)
@ContextConfiguration(classes = {DailySummaryController.class, ApiExceptionHandler.class})
class DailySummaryControllerTest {
    @Autowired MockMvc mvc;
    @MockBean DailySummaryService service;
    private final LocalDate date = LocalDate.of(2026, 11, 1);

    @Test void internalPostAlwaysForcesRefreshAndRequiresDate() throws Exception {
        mvc.perform(post("/internal/sensors/sensor-001/daily-summary").param("date", date.toString()))
                .andExpect(status().isOk());
        verify(service).getOrCompute("sensor-001", date, true);
        mvc.perform(post("/internal/sensors/sensor-001/daily-summary")).andExpect(status().isBadRequest());
        mvc.perform(post("/internal/sensors/sensor-001/daily-summary").param("date", "invalid"))
                .andExpect(status().isBadRequest());
    }
    @Test void onlyMissingSensorHasBusinessCode() throws Exception {
        when(service.getOrCompute("absent", date, true)).thenThrow(new SensorNotFoundException("absent"));
        mvc.perform(post("/internal/sensors/absent/daily-summary").param("date", date.toString()))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("SENSOR_NOT_FOUND"));
        when(service.getOrCompute("sensor-001", date, true)).thenThrow(new NoMeasurementsException("sensor-001", date.toString()));
        mvc.perform(post("/internal/sensors/sensor-001/daily-summary").param("date", date.toString()))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").doesNotExist());
        mvc.perform(get("/missing-route")).andExpect(status().isNotFound()).andExpect(jsonPath("$.code").doesNotExist());
    }
    @Test void publicGetPassesDateAndRefresh() throws Exception {
        mvc.perform(get("/api/sensors/sensor-001/daily-summary").param("date", date.toString()).param("refresh", "true"))
                .andExpect(status().isOk());
        verify(service).getOrCompute("sensor-001", date, true);
        mvc.perform(get("/api/sensors/sensor-001/daily-summary").param("date", date.toString()))
                .andExpect(status().isOk());
        verify(service).getOrCompute("sensor-001", date, false);
    }
}
