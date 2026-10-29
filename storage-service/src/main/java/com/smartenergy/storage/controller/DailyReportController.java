package com.smartenergy.storage.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import com.smartenergy.storage.exception.ApiErrorResponse;

import com.smartenergy.storage.mongo.dto.DailyReportResponse;
import com.smartenergy.storage.mongo.service.DailyReportService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/** Consultation d'un instantané quotidien enregistré dans MongoDB. */
@RestController
@RequestMapping("/api/sensors")
public class DailyReportController {
    private final DailyReportService reports;

    public DailyReportController(DailyReportService reports) {
        this.reports = reports;
    }

    @Operation(summary = "Lire ou créer un instantané quotidien immuable à partir du résumé MongoDB")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Succès"),
        @ApiResponse(responseCode = "400", description = "Paramètres ou corps invalides", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
        @ApiResponse(responseCode = "404", description = "Capteur ou mesures absents", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
        @ApiResponse(responseCode = "500", description = "Erreur de stockage", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @GetMapping("/{id}/daily-report")
    public ResponseEntity<DailyReportResponse> dailyReport(
            @Parameter(description = "Identifiant du capteur", example = "SENSOR-001") @PathVariable String id,
            @Parameter(description = "Jour UTC du rapport", example = "2026-10-22") @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ResponseEntity.ok(reports.getOrCreate(id, date));
    }
}
