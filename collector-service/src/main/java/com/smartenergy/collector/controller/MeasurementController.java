package com.smartenergy.collector.controller;

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

    @PostMapping
    public ResponseEntity<Void> receiveMeasurement(@Valid @RequestBody EnergyMeasurementRequest request) {
        publisher.publish(request);
        return ResponseEntity.accepted().build();
    }
}
