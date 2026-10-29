package com.smartenergy.storage.controller;

import com.smartenergy.storage.service.AnalyticsService;
import com.smartenergy.storage.service.AnalyticsSummary;
import com.smartenergy.storage.service.MeasurementAnomaly;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;

/** Consultation descriptive et anomalies par seuil sur une plage UTC bornée. */
@RestController
@RequestMapping("/api/analytics")
public class AnalyticsController {
    private final AnalyticsService service;

    public AnalyticsController(AnalyticsService service) {
        this.service = service;
    }

    @GetMapping("/{sensorId}/summary")
    @io.swagger.v3.oas.annotations.Operation(summary = "Résumé descriptif sur une période UTC", description = "Bornes inclusives from/to, durée maximale 31 jours. Énergie en kWh par maintien de puissance entre échantillons (intervalle plafonné à 24 h), dernier intervalle configuré borné par to. Une fenêtre vide retourne count=0 et indicateurs à zéro.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Résumé calculé")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Dates absentes, invalides, inversées ou plage supérieure à 31 jours")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Capteur non enregistré")
    public ResponseEntity<AnalyticsSummary> summary(@PathVariable String sensorId,
                                                    @RequestParam Instant from,
                                                    @RequestParam Instant to) {
        return ResponseEntity.ok(service.summarize(sensorId, from, to));
    }

    @GetMapping("/{sensorId}/anomalies")
    @io.swagger.v3.oas.annotations.Operation(summary = "Mesures dépassant les seuils actuels du capteur", description = "Plage UTC de 31 jours maximum. Motifs POWER_ABOVE_MAX, VOLTAGE_BELOW_MIN, VOLTAGE_ABOVE_MAX, TEMPERATURE_ABOVE_MAX. Égalité au seuil acceptée ; seuils actuels appliqués rétroactivement.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Liste des mesures et motifs, vide si aucun dépassement")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Période invalide ou excessive")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Capteur non enregistré")
    public ResponseEntity<List<MeasurementAnomaly>> anomalies(@PathVariable String sensorId,
                                                               @RequestParam Instant from,
                                                               @RequestParam Instant to) {
        return ResponseEntity.ok(service.findAnomalies(sensorId, from, to));
    }
}
