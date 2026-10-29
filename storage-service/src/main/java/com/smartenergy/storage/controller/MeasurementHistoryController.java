package com.smartenergy.storage.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import com.smartenergy.storage.exception.ApiErrorResponse;

import com.smartenergy.storage.cassandra.entity.MeasurementEntity;
import com.smartenergy.storage.cassandra.service.CassandraMeasurementService;
import com.smartenergy.storage.service.LatestMeasurementService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Contrôleur REST d'accès aux mesures historiques stockées dans Cassandra.
 */
@RestController
@RequestMapping("/api/measurements")
public class MeasurementHistoryController {

    private final CassandraMeasurementService cassandraService;
    private final LatestMeasurementService latestMeasurementService;

    public MeasurementHistoryController(CassandraMeasurementService cassandraService,
                                        LatestMeasurementService latestMeasurementService) {
        this.cassandraService = cassandraService;
        this.latestMeasurementService = latestMeasurementService;
    }

    /**
     * Historique d'un capteur pour un jour donné.
     *
     * <p>GET /api/measurements/{sensorId}/day/{day}</p>
     *
     * @param sensorId identifiant du capteur
     * @param day      date au format ISO-8601 (ex: 2026-10-21)
     * @return liste des mesures, plus récent en premier
     */
    @Operation(summary = "Lire les mesures du jour UTC, ordre décroissant")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Succès"),
        @ApiResponse(responseCode = "400", description = "Paramètres ou corps invalides", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
        @ApiResponse(responseCode = "500", description = "Erreur de stockage", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @GetMapping("/{sensorId}/day/{day}")
    public ResponseEntity<List<MeasurementEntity>> getByDay(
            @Parameter(description = "Identifiant du capteur", example = "SENSOR-001") @PathVariable String sensorId,
            @Parameter(description = "Jour UTC", example = "2026-10-22") @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate day) {
        List<MeasurementEntity> result = cassandraService.findByDay(sensorId, day);
        return ResponseEntity.ok(result);
    }

    /**
     * Historique d'un capteur entre deux timestamps.
     *
     * <p>GET /api/measurements/{sensorId}/history?from=...&to=...</p>
     *
     * @param sensorId identifiant du capteur
     * @param from     borne inférieure ISO-8601 UTC (ex: 2026-10-21T00:00:00Z)
     * @param to       borne supérieure ISO-8601 UTC (ex: 2026-10-21T23:59:59Z)
     * @return liste des mesures dans l'intervalle, plus récent en premier par jour
     */
    @Operation(summary = "Lire les mesures entre deux instants UTC inclus, ordre décroissant", description = "Période de 31 jours maximum ; plage excessive rejetée en HTTP 400 avant lecture Cassandra.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Mesures dans la plage", content = @Content(array = @ArraySchema(schema = @Schema(implementation = MeasurementEntity.class)))),
        @ApiResponse(responseCode = "400", description = "Paramètres ou corps invalides", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
        @ApiResponse(responseCode = "500", description = "Erreur de stockage", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @GetMapping("/{sensorId}/history")
    public ResponseEntity<?> getHistory(
            @Parameter(description = "Identifiant du capteur", example = "SENSOR-001") @PathVariable String sensorId,
            @Parameter(description = "Début UTC inclus", example = "2026-10-22T00:00:00Z") @RequestParam Instant from,
            @Parameter(description = "Fin UTC incluse", example = "2026-10-22T23:59:59Z") @RequestParam Instant to) {
        if (from.isAfter(to)) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "Requête invalide",
                    "message", "'from' doit être antérieur ou égal à 'to'"
            ));
        }
        List<MeasurementEntity> result = cassandraService.findByRange(sensorId, from, to);
        return ResponseEntity.ok(result);
    }

    /**
     * Dernière mesure connue d'un capteur pour un jour.
     *
     * <p>GET /api/measurements/{sensorId}/latest?day=...</p>
     *
     * @param sensorId identifiant du capteur
     * @param day      date au format ISO-8601
     * @return la mesure la plus récente ou 404
     */
    @Operation(summary = "Lire la dernière mesure connue ; day limite la recherche à ce jour UTC")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Succès"),
        @ApiResponse(responseCode = "400", description = "Paramètres ou corps invalides", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
        @ApiResponse(responseCode = "404", description = "Capteur ou mesures absents", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
        @ApiResponse(responseCode = "500", description = "Erreur de stockage", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @GetMapping("/{sensorId}/latest")
    public ResponseEntity<MeasurementEntity> getLatest(
            @Parameter(description = "Identifiant du capteur", example = "SENSOR-001") @PathVariable String sensorId,
            @Parameter(description = "Jour UTC facultatif ; dernière mesure connue si absent", example = "2026-10-22") @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate day) {
        Optional<MeasurementEntity> latest = day == null
                ? latestMeasurementService.findLatestKnown(sensorId)
                : cassandraService.findLatest(sensorId, day);
        return latest
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}
