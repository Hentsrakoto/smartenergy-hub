package com.smartenergy.collector.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import com.smartenergy.collector.dto.ValidationErrorResponse;

import com.smartenergy.collector.dto.EnergyMeasurementRequest;
import com.smartenergy.collector.service.MeasurementPublisher;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Contrôleur d'ingestion REST des mesures énergétiques IoT.
 *
 * Valide le schéma et insère immédiatement l'événement dans Redis Streams.
 * Répond HTTP 202 Accepted une fois le message pris en charge dans le flux.
 */
@RestController
@RequestMapping("/api/measurements")
public class MeasurementController {

    private final MeasurementPublisher publisher;

    public MeasurementController(MeasurementPublisher publisher) {
        this.publisher = publisher;
    }

    @Operation(summary = "Accepter une mesure dans Redis Streams",
            description = "202 confirme la publication Redis, sans garantir la persistance finale dans Cassandra et MongoDB.",
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(content = @Content(
                    schema = @Schema(implementation = EnergyMeasurementRequest.class),
                    examples = @ExampleObject(value = "{\"sensorId\":\"SENSOR-001\",\"timestamp\":\"2026-10-22T12:00:00Z\",\"powerKw\":2.5,\"voltage\":230,\"temperature\":25}"))))
    @ApiResponses({
        @ApiResponse(responseCode = "202", description = "Événement accepté pour traitement asynchrone", content = @Content),
        @ApiResponse(responseCode = "400", description = "Mesure invalide ou JSON illisible", content = @Content(schema = @Schema(implementation = ValidationErrorResponse.class))),
        @ApiResponse(responseCode = "500", description = "Publication Redis échouée", content = @Content)
    })
    @PostMapping
    public ResponseEntity<Void> receiveMeasurement(@Valid @RequestBody EnergyMeasurementRequest request) {
        publisher.publish(request);
        return ResponseEntity.accepted().build();
    }
}
