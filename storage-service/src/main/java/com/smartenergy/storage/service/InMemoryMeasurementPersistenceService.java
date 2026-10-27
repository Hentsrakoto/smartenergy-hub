package com.smartenergy.storage.service;

import com.smartenergy.storage.model.EnergyMeasurementEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Implémentation en mémoire de la persistance pour l'Étape 4 (Redis Streams & Fiabilité).
 * Prépare et isole la persistance avant l'intégration Cassandra (Étape 5) et MongoDB (Étape 6/7).
 */
@Service
public class InMemoryMeasurementPersistenceService implements MeasurementPersistenceService {

    private static final Logger log = LoggerFactory.getLogger(InMemoryMeasurementPersistenceService.class);

    private final List<EnergyMeasurementEvent> storage = new CopyOnWriteArrayList<>();
    private final AtomicBoolean simulateFailure = new AtomicBoolean(false);

    @Override
    public void persist(EnergyMeasurementEvent event) {
        if (simulateFailure.get()) {
            log.error("Simulation d'échec de persistance pour l'événement '{}' (sensorId: {})",
                    event.recordId(), event.sensorId());
            throw new IllegalStateException("Échec simulé de la persistance pour tester la PEL (non-acquittement)");
        }

        storage.add(event);
        log.info("Mesure persistée avec succès: recordId='{}', sensorId='{}', timestamp='{}', powerKw={} kW",
                event.recordId(), event.sensorId(), event.timestamp(), event.powerKw());
    }

    @Override
    public List<EnergyMeasurementEvent> getPersistedEvents() {
        return Collections.unmodifiableList(new ArrayList<>(storage));
    }

    @Override
    public long getPersistedCount() {
        return storage.size();
    }

    @Override
    public void clear() {
        storage.clear();
    }

    public void setSimulateFailure(boolean fail) {
        this.simulateFailure.set(fail);
        log.warn("Mode simulation d'échec de persistance: {}", fail);
    }

    public boolean isSimulatingFailure() {
        return this.simulateFailure.get();
    }
}
