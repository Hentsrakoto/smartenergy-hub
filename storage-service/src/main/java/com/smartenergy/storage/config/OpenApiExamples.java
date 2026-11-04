package com.smartenergy.storage.config;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.models.examples.Example;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.Map;

/** Exemples pédagogiques fixes ; ne lisent ni ne créent aucune donnée métier. */
@Configuration
public class OpenApiExamples {
    @Bean
    OpenApiCustomizer consultationExamples(ObjectMapper mapper) throws JsonProcessingException {
        Object sensorRequest = mapper.readValue("""
                {"sensorId":"sensor-demo","name":"Compteur du cours","building":"Bâtiment A",
                 "maxPowerKw":5,"active":true,"samplingIntervalSeconds":60}
                """, Object.class);
        Object sensor = mapper.readValue("""
                {"sensorId":"sensor-demo","name":"Compteur du cours","building":"Bâtiment A","floor":null,
                 "maxPowerKw":5,"active":true,"tags":[],"minVoltage":150,"maxVoltage":300,
                 "maxTemperature":125,"samplingIntervalSeconds":60,
                 "createdAt":"2026-11-01T00:00:00Z","updatedAt":"2026-11-01T00:00:00Z"}
                """, Object.class);
        Object measurement = mapper.readValue("""
                {"sensorId":"sensor-demo","day":"2026-11-01","measuredAt":"2026-11-01T00:02:00Z",
                 "powerKw":6,"voltage":230,"temperature":22}
                """, Object.class);
        Object summary = mapper.readValue("""
                {"sensorId":"sensor-demo","date":"2026-11-01","measurementCount":3,"minPowerKw":1,
                 "avgPowerKw":3.333333,"maxPowerKw":6,"energyKwh":0.166667,"anomalyCount":1,
                 "updatedAt":"2026-11-01T00:03:00Z"}
                """, Object.class);
        Object analytics = mapper.readValue("""
                {"sensorId":"sensor-demo","from":"2026-11-01T00:00:00Z","to":"2026-11-01T00:03:00Z",
                 "measurementCount":3,"minPowerKw":1,"avgPowerKw":3.333333,"maxPowerKw":6,
                 "energyKwh":0.166667,"anomalyCount":1}
                """, Object.class);
        Object report = mapper.readValue("""
                {"sensorId":"sensor-demo","sensorName":"Compteur du cours","date":"2026-11-01",
                 "measurementCount":3,"energyKwh":0.166667,"anomalyCount":1,"status":"ALERT",
                 "generatedAt":"2026-11-01T00:03:00Z"}
                """, Object.class);

        return api -> {
            if (api.getPaths() == null) return;
            api.getPaths().forEach((path, item) -> item.readOperationsMap().forEach((method, operation) -> {
                if (!path.startsWith("/api/sensors") && !path.startsWith("/api/measurements")
                        && !path.startsWith("/api/analytics")) return;
                if (operation.getParameters() != null) operation.getParameters().forEach(parameter -> {
                    switch (parameter.getName()) {
                        case "sensorId", "id" -> parameter.setExample("sensor-demo");
                        case "day", "date" -> parameter.setExample("2026-11-01");
                        case "from" -> parameter.setExample("2026-11-01T00:00:00Z");
                        case "to" -> parameter.setExample("2026-11-01T00:03:00Z");
                        default -> { }
                    }
                });
                if (operation.getRequestBody() != null && path.startsWith("/api/sensors"))
                    addExample(operation.getRequestBody().getContent(), sensorRequest);
                Object success;
                if (path.endsWith("/daily-summary")) success = summary;
                else if (path.endsWith("/daily-report")) success = report;
                else if (path.endsWith("/summary")) success = analytics;
                else if (path.endsWith("/anomalies")) success = List.of(Map.of(
                        "measurement", measurement, "reasons", List.of("POWER_ABOVE_MAX")));
                else if (path.endsWith("/latest")) success = measurement;
                else if (path.startsWith("/api/measurements")) success = List.of(measurement);
                else if (path.equals("/api/sensors") && method.name().equals("GET")) success = List.of(sensor);
                else success = sensor;
                operation.getResponses().forEach((code, response) -> {
                    if (code.startsWith("2")) addExample(response.getContent(), success);
                    else if (List.of("400", "404", "409", "500").contains(code)) {
                        String error = switch (code) {
                            case "400" -> "Bad Request";
                            case "404" -> "Not Found";
                            case "409" -> "Conflict";
                            default -> "Internal Server Error";
                        };
                        String message = switch (code) {
                            case "400" -> "Paramètre ou corps de requête invalide";
                            case "404" -> "Capteur ou mesures introuvables";
                            case "409" -> "Le capteur sensor-demo existe déjà";
                            default -> "Erreur interne du serveur";
                        };
                        Object example = Map.of("status", Integer.parseInt(code), "error", error,
                                "message", message, "timestamp", "2026-11-01T00:03:00Z",
                                "path", path.replace("{id}", "sensor-demo")
                                        .replace("{sensorId}", "sensor-demo").replace("{day}", "2026-11-01"));
                        // Conserve le schéma généré, ajoute un exemple JSON de l'erreur commune.
                        if (response.getContent() == null) response.setContent(new Content());
                        response.getContent().addMediaType("application/json", new MediaType()
                                .schema(new Schema<>().$ref("#/components/schemas/ApiErrorResponse"))
                                .addExamples("cours", new Example().value(example)));
                    }
                });
            }));
        };
    }

    private static void addExample(Content content, Object value) {
        if (content != null) content.values().forEach(media -> media.addExamples("cours",
                new Example().summary("Exemple fictif pour le cours").value(value)));
    }
}
