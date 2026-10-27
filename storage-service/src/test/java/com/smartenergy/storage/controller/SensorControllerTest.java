package com.smartenergy.storage.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartenergy.storage.mongo.dto.DailySummaryResponse;
import com.smartenergy.storage.mongo.dto.SensorResponse;
import com.smartenergy.storage.mongo.exception.DuplicateSensorException;
import com.smartenergy.storage.mongo.exception.SensorNotFoundException;
import com.smartenergy.storage.mongo.service.DailySummaryService;
import com.smartenergy.storage.mongo.service.SensorService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = SensorController.class)
class SensorControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private SensorService sensorService;

    @MockBean
    private DailySummaryService dailySummaryService;

    @Test
    @DisplayName("POST /api/sensors doit créer un capteur et retourner 201")
    void shouldCreateSensor() throws Exception {
        when(sensorService.create(any())).thenReturn(sensorResponse("SENSOR-001"));

        mockMvc.perform(post("/api/sensors")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(sensorJson("SENSOR-001", "Compteur salle serveur", 8.0)))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/sensors/SENSOR-001"))
                .andExpect(jsonPath("$.sensorId").value("SENSOR-001"))
                .andExpect(jsonPath("$.name").value("Compteur SENSOR-001"))
                .andExpect(jsonPath("$.active").value(true))
                .andExpect(jsonPath("$.tags[0]").value("critical"));
    }

    @Test
    @DisplayName("POST /api/sensors doit rejeter un corps invalide avec 400")
    void shouldRejectInvalidSensor() throws Exception {
        mockMvc.perform(post("/api/sensors")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(sensorJson("SENSOR-002", "", -1.0)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.fieldErrors.name").exists())
                .andExpect(jsonPath("$.fieldErrors.maxPowerKw").exists());

        verify(sensorService, never()).create(any());
    }

    @Test
    @DisplayName("POST /api/sensors doit retourner 409 si le capteur existe déjà")
    void shouldReturnConflictForDuplicateSensor() throws Exception {
        when(sensorService.create(any())).thenThrow(new DuplicateSensorException("SENSOR-001"));

        mockMvc.perform(post("/api/sensors")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(sensorJson("SENSOR-001", "Doublon", 8.0)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.message").value(
                        org.hamcrest.Matchers.containsString("existe déjà")));
    }

    @Test
    @DisplayName("POST /api/sensors doit retourner 400 si sensorId est absent")
    void shouldRejectMissingSensorId() throws Exception {
        when(sensorService.create(any()))
                .thenThrow(new IllegalArgumentException("sensorId est obligatoire"));

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("name", "Sans identifiant");
        body.put("maxPowerKw", 8.0);
        body.put("active", true);

        mockMvc.perform(post("/api/sensors")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("sensorId est obligatoire"));
    }

    @Test
    @DisplayName("POST /api/sensors doit refuser un identifiant incompatible avec le Collector")
    void shouldRejectInvalidSensorIdBeforeSaving() throws Exception {
        mockMvc.perform(post("/api/sensors")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(sensorJson("sensor/001", "Capteur", 8.0)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.sensorId").exists());

        verify(sensorService, never()).create(any());
    }

    @Test
    @DisplayName("GET /api/sensors doit lister les capteurs")
    void shouldListSensors() throws Exception {
        when(sensorService.findAll()).thenReturn(List.of(
                sensorResponse("SENSOR-001"), sensorResponse("SENSOR-002")));

        mockMvc.perform(get("/api/sensors"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].sensorId").value("SENSOR-001"))
                .andExpect(jsonPath("$[1].sensorId").value("SENSOR-002"));
    }

    @Test
    @DisplayName("GET /api/sensors/{id} doit retourner un capteur")
    void shouldGetSensorById() throws Exception {
        when(sensorService.findById("SENSOR-001")).thenReturn(sensorResponse("SENSOR-001"));

        mockMvc.perform(get("/api/sensors/SENSOR-001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sensorId").value("SENSOR-001"))
                .andExpect(jsonPath("$.maxPowerKw").value(8.0))
                .andExpect(jsonPath("$.minVoltage").value(150.0))
                .andExpect(jsonPath("$.samplingIntervalSeconds").value(60));
    }

    @Test
    @DisplayName("GET /api/sensors/{id} doit retourner 404 si le capteur est absent")
    void shouldReturnNotFoundForUnknownSensor() throws Exception {
        when(sensorService.findById("UNKNOWN")).thenThrow(new SensorNotFoundException("UNKNOWN"));

        mockMvc.perform(get("/api/sensors/UNKNOWN"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value(
                        org.hamcrest.Matchers.containsString("UNKNOWN")));
    }

    @Test
    @DisplayName("PUT /api/sensors/{id} doit mettre à jour un capteur")
    void shouldUpdateSensor() throws Exception {
        SensorResponse updated = sensorResponse("SENSOR-001");
        when(sensorService.update(eq("SENSOR-001"), any())).thenReturn(updated);

        mockMvc.perform(put("/api/sensors/SENSOR-001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(sensorJson("SENSOR-001", "Nom modifié", 9.0)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sensorId").value("SENSOR-001"));
    }

    @Test
    @DisplayName("PUT /api/sensors/{id} doit rejeter un corps invalide avec 400")
    void shouldRejectInvalidUpdate() throws Exception {
        mockMvc.perform(put("/api/sensors/SENSOR-001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(sensorJson("SENSOR-001", "", 9.0)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.name").exists());

        verify(sensorService, never()).update(any(), any());
    }

    @Test
    @DisplayName("GET /api/sensors/{id}/daily-summary doit retourner l'agrégat demandé")
    void shouldReturnDailySummary() throws Exception {
        LocalDate date = LocalDate.of(2026, 10, 22);
        when(dailySummaryService.getOrCompute("SENSOR-001", date, false))
                .thenReturn(dailySummaryResponse());

        mockMvc.perform(get("/api/sensors/SENSOR-001/daily-summary")
                        .param("date", "2026-10-22"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sensorId").value("SENSOR-001"))
                .andExpect(jsonPath("$.date").value("2026-10-22"))
                .andExpect(jsonPath("$.avgPowerKw").value(3.42))
                .andExpect(jsonPath("$.maxPowerKw").value(7.81))
                .andExpect(jsonPath("$.energyKwh").value(68.2))
                .andExpect(jsonPath("$.anomalyCount").value(2));
    }

    @Test
    @DisplayName("GET /api/sensors/{id}/daily-summary doit utiliser la date UTC du jour par défaut")
    void shouldUseCurrentUtcDateByDefault() throws Exception {
        when(dailySummaryService.getOrCompute(eq("SENSOR-001"), any(LocalDate.class), eq(false)))
                .thenReturn(dailySummaryResponse());

        mockMvc.perform(get("/api/sensors/SENSOR-001/daily-summary"))
                .andExpect(status().isOk());

        verify(dailySummaryService).getOrCompute(
                eq("SENSOR-001"), eq(LocalDate.now(ZoneOffset.UTC)), eq(false));
    }

    @Test
    @DisplayName("GET /api/sensors/{id}/daily-summary doit propager refresh=true")
    void shouldForwardRefreshParameter() throws Exception {
        when(dailySummaryService.getOrCompute("SENSOR-001", LocalDate.of(2026, 10, 22), true))
                .thenReturn(dailySummaryResponse());

        mockMvc.perform(get("/api/sensors/SENSOR-001/daily-summary")
                        .param("date", "2026-10-22")
                        .param("refresh", "true"))
                .andExpect(status().isOk());

        verify(dailySummaryService).getOrCompute("SENSOR-001", LocalDate.of(2026, 10, 22), true);
    }

    @Test
    @DisplayName("GET /api/sensors/{id}/daily-summary doit rejeter une date invalide")
    void shouldRejectInvalidDate() throws Exception {
        mockMvc.perform(get("/api/sensors/SENSOR-001/daily-summary")
                        .param("date", "2026-13-40"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value(
                        org.hamcrest.Matchers.containsString("date")));
    }

    @Test
    @DisplayName("GET /api/sensors/{id}/daily-summary doit retourner 404 sans mesure")
    void shouldReturnNotFoundWhenNoMeasurements() throws Exception {
        when(dailySummaryService.getOrCompute(eq("SENSOR-001"), any(LocalDate.class), eq(false)))
                .thenThrow(new com.smartenergy.storage.mongo.exception.NoMeasurementsException(
                        "SENSOR-001", "2026-10-22"));

        mockMvc.perform(get("/api/sensors/SENSOR-001/daily-summary"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value(
                        org.hamcrest.Matchers.containsString("Aucune mesure")));
    }

    private String sensorJson(String sensorId, String name, double maxPowerKw) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("sensorId", sensorId);
        body.put("name", name);
        body.put("building", "Bâtiment A");
        body.put("floor", 1);
        body.put("maxPowerKw", maxPowerKw);
        body.put("active", true);
        body.put("tags", List.of("critical", "server-room"));
        return objectMapper.writeValueAsString(body);
    }

    private SensorResponse sensorResponse(String sensorId) {
        Instant timestamp = Instant.parse("2026-10-22T10:00:00Z");
        return new SensorResponse(
                sensorId,
                "Compteur " + sensorId,
                "Bâtiment A",
                1,
                8.0,
                true,
                List.of("critical", "server-room"),
                150.0,
                300.0,
                125.0,
                60,
                timestamp,
                timestamp
        );
    }

    private DailySummaryResponse dailySummaryResponse() {
        return new DailySummaryResponse(
                "SENSOR-001",
                "2026-10-22",
                3.42,
                7.81,
                68.2,
                2,
                1440,
                1.0,
                Instant.parse("2026-10-22T23:59:00Z")
        );
    }
}
