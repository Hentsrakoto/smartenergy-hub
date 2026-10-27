package com.smartenergy.storage.controller;

import com.smartenergy.storage.mongo.dto.DailySummaryResponse;
import com.smartenergy.storage.mongo.dto.SensorRequest;
import com.smartenergy.storage.mongo.dto.SensorResponse;
import com.smartenergy.storage.mongo.service.DailySummaryService;
import com.smartenergy.storage.mongo.service.SensorService;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

/**
 * API REST de gestion des capteurs et de leurs agrégats quotidiens (MongoDB).
 *
 * <ul>
 *   <li>{@code POST /api/sensors} : créer un capteur ;</li>
 *   <li>{@code GET /api/sensors} : lister les capteurs ;</li>
 *   <li>{@code GET /api/sensors/{id}} : obtenir un capteur ;</li>
 *   <li>{@code PUT /api/sensors/{id}} : modifier un capteur ;</li>
 *   <li>{@code GET /api/sensors/{id}/daily-summary} : obtenir l'agrégat quotidien.</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/sensors")
public class SensorController {

    private final SensorService sensorService;
    private final DailySummaryService dailySummaryService;

    public SensorController(SensorService sensorService, DailySummaryService dailySummaryService) {
        this.sensorService = sensorService;
        this.dailySummaryService = dailySummaryService;
    }

    @PostMapping
    public ResponseEntity<SensorResponse> create(@Valid @RequestBody SensorRequest request) {
        SensorResponse created = sensorService.create(request);
        return ResponseEntity
                .created(URI.create("/api/sensors/" + created.sensorId()))
                .body(created);
    }

    @GetMapping
    public ResponseEntity<List<SensorResponse>> findAll() {
        return ResponseEntity.ok(sensorService.findAll());
    }

    @GetMapping("/{id}")
    public ResponseEntity<SensorResponse> findById(@PathVariable String id) {
        return ResponseEntity.ok(sensorService.findById(id));
    }

    @PutMapping("/{id}")
    public ResponseEntity<SensorResponse> update(
            @PathVariable String id,
            @Valid @RequestBody SensorRequest request) {
        return ResponseEntity.ok(sensorService.update(id, request));
    }

    /**
     * Agrégat quotidien d'un capteur.
     *
     * <p>Si {@code date} est absente, la date UTC du jour est utilisée. Si l'agrégat
     * n'existe pas encore dans MongoDB, il est calculé depuis Cassandra puis stocké.
     * {@code refresh=true} force un recalcul.</p>
     */
    @GetMapping("/{id}/daily-summary")
    public ResponseEntity<DailySummaryResponse> dailySummary(
            @PathVariable String id,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(defaultValue = "false") boolean refresh) {
        LocalDate targetDate = date != null ? date : LocalDate.now(ZoneOffset.UTC);
        return ResponseEntity.ok(dailySummaryService.getOrCompute(id, targetDate, refresh));
    }
}
