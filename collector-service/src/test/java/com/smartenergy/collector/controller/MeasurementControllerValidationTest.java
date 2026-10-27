package com.smartenergy.collector.controller;

import com.smartenergy.collector.exception.GlobalExceptionHandler;
import com.smartenergy.collector.service.MeasurementPublisher;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.notNullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(MeasurementController.class)
@Import(GlobalExceptionHandler.class)
class MeasurementControllerValidationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private MeasurementPublisher publisher;

    @Test
    @DisplayName("Requête JSON valide doit publier dans Redis et retourner HTTP 202 Accepted")
    void testReceiveValidMeasurementReturns202() throws Exception {
        when(publisher.publish(any())).thenReturn(RecordId.of("1792507200000-0"));

        String validJson = """
                {
                    "sensorId": "sensor-101",
                    "timestamp": "2026-10-20T14:30:00Z",
                    "powerKw": 4.5,
                    "voltage": 232.0,
                    "temperature": 21.5
                }
                """;

        mockMvc.perform(post("/api/measurements")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validJson))
                .andExpect(status().isAccepted());

        verify(publisher, times(1)).publish(any());
    }

    @Test
    @DisplayName("Mesure sans champ optionnel temperature doit publier et retourner HTTP 202")
    void testReceiveValidMeasurementWithoutTemperatureReturns202() throws Exception {
        when(publisher.publish(any())).thenReturn(RecordId.of("1792507200001-0"));

        String validJson = """
                {
                    "sensorId": "sensor-102",
                    "timestamp": "2026-10-20T14:31:00Z",
                    "powerKw": 0.0,
                    "voltage": 229.5
                }
                """;

        mockMvc.perform(post("/api/measurements")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validJson))
                .andExpect(status().isAccepted());

        verify(publisher, times(1)).publish(any());
    }

    @Test
    @DisplayName("Requête avec puissance négative doit être rejetée en 400 et NE PAS publier dans Redis")
    void testNegativePowerReturns400AndNoPublish() throws Exception {
        String invalidJson = """
                {
                    "sensorId": "sensor-101",
                    "timestamp": "2026-10-20T14:30:00Z",
                    "powerKw": -3.2,
                    "voltage": 230.0,
                    "temperature": 25.0
                }
                """;

        mockMvc.perform(post("/api/measurements")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.fieldErrors.powerKw", notNullValue()))
                .andExpect(jsonPath("$.fieldErrors.powerKw", containsString("positive")));

        verify(publisher, never()).publish(any());
    }

    @Test
    @DisplayName("Requête avec sensorId vide doit être rejetée en 400 et NE PAS publier dans Redis")
    void testBlankSensorIdReturns400AndNoPublish() throws Exception {
        String invalidJson = """
                {
                    "sensorId": "  ",
                    "timestamp": "2026-10-20T14:30:00Z",
                    "powerKw": 2.0,
                    "voltage": 230.0
                }
                """;

        mockMvc.perform(post("/api/measurements")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.fieldErrors.sensorId", notNullValue()));

        verify(publisher, never()).publish(any());
    }

    @Test
    @DisplayName("Requête avec tension hors bornes doit être rejetée en 400 et NE PAS publier dans Redis")
    void testVoltageOutOfRangeReturns400AndNoPublish() throws Exception {
        String invalidJson = """
                {
                    "sensorId": "sensor-101",
                    "timestamp": "2026-10-20T14:30:00Z",
                    "powerKw": 2.0,
                    "voltage": 90.0
                }
                """;

        mockMvc.perform(post("/api/measurements")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.fieldErrors.voltage", notNullValue()));

        verify(publisher, never()).publish(any());
    }

    @Test
    @DisplayName("JSON malformé doit être rejeté en 400 et NE PAS publier dans Redis")
    void testMalformedJsonReturns400AndNoPublish() throws Exception {
        String malformedJson = """
                {
                    "sensorId": "sensor-101",
                    "timestamp": "date-invalide-non-iso",
                    "powerKw": 2.0,
                    "voltage": 230.0
                }
                """;

        mockMvc.perform(post("/api/measurements")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(malformedJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.fieldErrors.payload", notNullValue()));

        verify(publisher, never()).publish(any());
    }
}
