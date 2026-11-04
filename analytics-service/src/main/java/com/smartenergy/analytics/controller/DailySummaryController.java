package com.smartenergy.analytics.controller;
import com.smartenergy.analytics.mongo.dto.DailySummaryResponse;
import com.smartenergy.analytics.mongo.service.DailySummaryService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDate;
import java.time.ZoneOffset;
@RestController
public class DailySummaryController {
    private final DailySummaryService summaries;
    public DailySummaryController(DailySummaryService summaries) { this.summaries = summaries; }
    @GetMapping("/api/sensors/{id}/daily-summary")
    public DailySummaryResponse summary(@PathVariable String id,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(defaultValue = "false") boolean refresh) {
        return summaries.getOrCompute(id, date == null ? LocalDate.now(ZoneOffset.UTC) : date, refresh);
    }
    @io.swagger.v3.oas.annotations.Operation(summary = "Recalcul interne forcé du résumé quotidien UTC")
    @PostMapping("/internal/sensors/{id}/daily-summary")
    public DailySummaryResponse recompute(@PathVariable String id,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return summaries.getOrCompute(id, date, true);
    }
}
