package com.smartenergy.storage.controller;

import com.smartenergy.storage.cassandra.entity.MeasurementEntity;
import com.smartenergy.storage.cassandra.service.CassandraMeasurementService;
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

    public MeasurementHistoryController(CassandraMeasurementService cassandraService) {
        this.cassandraService = cassandraService;
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
    @GetMapping("/{sensorId}/day/{day}")
    public ResponseEntity<List<MeasurementEntity>> getByDay(
            @PathVariable String sensorId,
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate day) {
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
    @GetMapping("/{sensorId}/history")
    public ResponseEntity<?> getHistory(
            @PathVariable String sensorId,
            @RequestParam Instant from,
            @RequestParam Instant to) {
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
    @GetMapping("/{sensorId}/latest")
    public ResponseEntity<?> getLatest(
            @PathVariable String sensorId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate day) {
        Optional<MeasurementEntity> latest = cassandraService.findLatest(sensorId, day);
        return latest
                .<ResponseEntity<?>>map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}
