package com.smartenergy.storage.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import com.smartenergy.storage.exception.ApiErrorResponse;

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

    @Operation(summary = "Créer un capteur")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Succès"),
        @ApiResponse(responseCode = "400", description = "Paramètres ou corps invalides", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
        @ApiResponse(responseCode = "409", description = "Identifiant existant", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
        @ApiResponse(responseCode = "500", description = "Erreur de stockage", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @PostMapping
    public ResponseEntity<SensorResponse> create(@Valid @RequestBody SensorRequest request) {
        SensorResponse created = sensorService.create(request);
        return ResponseEntity
                .created(URI.create("/api/sensors/" + created.sensorId()))
                .body(created);
    }

    @Operation(summary = "Lister les capteurs")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Succès"),
        @ApiResponse(responseCode = "500", description = "Erreur de stockage", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @GetMapping
    public ResponseEntity<List<SensorResponse>> findAll() {
        return ResponseEntity.ok(sensorService.findAll());
    }

    @Operation(summary = "Consulter les métadonnées")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Succès"),
        @ApiResponse(responseCode = "400", description = "Paramètres ou corps invalides", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
        @ApiResponse(responseCode = "404", description = "Capteur ou mesures absents", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
        @ApiResponse(responseCode = "500", description = "Erreur de stockage", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @GetMapping("/{id}")
    public ResponseEntity<SensorResponse> findById(@Parameter(description = "Identifiant du capteur", example = "SENSOR-001") @PathVariable String id) {
        return ResponseEntity.ok(sensorService.findById(id));
    }

    @Operation(summary = "Modifier les métadonnées et seuils")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Succès"),
        @ApiResponse(responseCode = "400", description = "Paramètres ou corps invalides", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
        @ApiResponse(responseCode = "404", description = "Capteur ou mesures absents", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
        @ApiResponse(responseCode = "500", description = "Erreur de stockage", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @PutMapping("/{id}")
    public ResponseEntity<SensorResponse> update(
            @Parameter(description = "Identifiant du capteur", example = "SENSOR-001") @PathVariable String id,
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
    @Operation(summary = "Lire un résumé quotidien ; date UTC courante par défaut, refresh force le recalcul")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Succès"),
        @ApiResponse(responseCode = "400", description = "Paramètres ou corps invalides", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
        @ApiResponse(responseCode = "404", description = "Capteur ou mesures absents", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
        @ApiResponse(responseCode = "500", description = "Erreur de stockage", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @GetMapping("/{id}/daily-summary")
    public ResponseEntity<DailySummaryResponse> dailySummary(
            @Parameter(description = "Identifiant du capteur", example = "SENSOR-001") @PathVariable String id,
            @Parameter(description = "Jour UTC ; aujourd’hui si absent", example = "2026-10-22")
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @Parameter(description = "Forcer le recalcul depuis Cassandra", example = "false") @RequestParam(defaultValue = "false") boolean refresh) {
        LocalDate targetDate = date != null ? date : LocalDate.now(ZoneOffset.UTC);
        return ResponseEntity.ok(dailySummaryService.getOrCompute(id, targetDate, refresh));
    }
}
