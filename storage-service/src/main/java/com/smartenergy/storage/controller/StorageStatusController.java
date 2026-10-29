package com.smartenergy.storage.controller;

import com.smartenergy.storage.config.RedisStreamProperties;
import com.smartenergy.storage.model.EnergyMeasurementEvent;
import com.smartenergy.storage.service.InMemoryMeasurementPersistenceService;
import com.smartenergy.storage.service.MeasurementPersistenceService;
import com.smartenergy.storage.service.RedisPendingMessageRecoveryService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(
        name = "smartenergy.diagnostics.enabled", havingValue = "true")
@RequestMapping("/api/storage")
public class StorageStatusController {

    private final MeasurementPersistenceService persistenceService;
    private final RedisStreamProperties properties;
    private final RedisPendingMessageRecoveryService recoveryService;

    public StorageStatusController(
            MeasurementPersistenceService persistenceService,
            RedisStreamProperties properties,
            RedisPendingMessageRecoveryService recoveryService) {
        this.persistenceService = persistenceService;
        this.properties = properties;
        this.recoveryService = recoveryService;
    }

    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getStatus() {
        boolean simulatingFailure = false;
        if (persistenceService instanceof InMemoryMeasurementPersistenceService inMemory) {
            simulatingFailure = inMemory.isSimulatingFailure();
        }

        return ResponseEntity.ok(Map.of(
                "service", "storage-service",
                "status", "UP",
                "stream", properties.getName(),
                "group", properties.getGroup(),
                "consumerName", properties.getConsumerName(),
                "persistedCount", persistenceService.getPersistedCount(),
                "simulatingFailure", simulatingFailure
        ));
    }

    @GetMapping("/events")
    public ResponseEntity<List<EnergyMeasurementEvent>> getEvents() {
        return ResponseEntity.ok(persistenceService.getPersistedEvents());
    }

    @PostMapping("/simulate-failure")
    public ResponseEntity<Map<String, Object>> simulateFailure(@RequestParam boolean enabled) {
        if (persistenceService instanceof InMemoryMeasurementPersistenceService inMemory) {
            inMemory.setSimulateFailure(enabled);
            return ResponseEntity.ok(Map.of(
                    "simulatingFailure", enabled,
                    "message", enabled ? "Simulation d'échec activée (aucun XACK envoyé)" : "Simulation d'échec désactivée"
            ));
        }
        return ResponseEntity.badRequest().body(Map.of("error", "Service de persistance non configurable pour simulation"));
    }

    @PostMapping("/recover-pending")
    public ResponseEntity<Map<String, Object>> triggerRecovery() {
        int recovered = recoveryService.recoverPendingMessages();
        return ResponseEntity.ok(Map.of(
                "recoveredCount", recovered,
                "currentPersistedCount", persistenceService.getPersistedCount()
        ));
    }
}
