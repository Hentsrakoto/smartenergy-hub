package com.smartenergy.storage.service;

import com.smartenergy.storage.cassandra.service.CassandraMeasurementService;
import com.smartenergy.storage.model.EnergyMeasurementEvent;
import com.smartenergy.storage.mongo.exception.SensorNotFoundException;
import com.smartenergy.storage.mongo.service.DailySummaryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Implémentation de la persistance des mesures dans Cassandra.
 *
 * <p>Cette implémentation est marquée {@code @Primary} et remplace
 * {@link InMemoryMeasurementPersistenceService} en production.</p>
 *
 * <p><strong>Idempotence :</strong> Cassandra garantit nativement qu'une réinsertion
 * sur la même clé primaire {@code (sensor_id, day, measured_at)} écrase la ligne
 * sans créer de doublon. Le résumé MongoDB est recalculé depuis Cassandra et
 * enregistré sous un identifiant stable ; rejouer un événement ne double donc
 * pas ses statistiques.</p>
 */
@Service
@Primary
public class CassandraPersistenceService implements MeasurementPersistenceService {

    private static final Logger log = LoggerFactory.getLogger(CassandraPersistenceService.class);

    private final CassandraMeasurementService cassandraService;
    private final DailySummaryService dailySummaryService;
    private final MeterRegistry metrics;
    private final AtomicLong persistedCount = new AtomicLong(0);
    private static final int LOCK_STRIPES = 64;
    private final Object[] dayLocks = new Object[LOCK_STRIPES];

    // Cache léger des derniers events (pour diagnostics via /api/storage/events)
    private final List<EnergyMeasurementEvent> recentEvents = new CopyOnWriteArrayList<>();
    private static final int MAX_RECENT = 50;

    public CassandraPersistenceService(CassandraMeasurementService cassandraService,
                                       DailySummaryService dailySummaryService, MeterRegistry metrics) {
        this.cassandraService = cassandraService;
        this.dailySummaryService = dailySummaryService;
        this.metrics = metrics;
        metrics.counter("smartenergy.storage.persistence.errors");
        metrics.timer("smartenergy.storage.persistence", "outcome", "success");
        metrics.timer("smartenergy.storage.persistence", "outcome", "error");
        for (int i = 0; i < dayLocks.length; i++) {
            dayLocks[i] = new Object();
        }
    }

    @Override
    public void persist(EnergyMeasurementEvent event) {
        Timer.Sample sample = Timer.start(metrics);
        String outcome = "success";
        try {
            persistMeasurement(event);
        } catch (RuntimeException exception) {
            outcome = "error";
            metrics.counter("smartenergy.storage.persistence.errors").increment();
            throw exception;
        } finally {
            sample.stop(metrics.timer("smartenergy.storage.persistence", "outcome", outcome));
        }
    }

    private void persistMeasurement(EnergyMeasurementEvent event) {
        LocalDate day = event.timestamp().atZone(ZoneOffset.UTC).toLocalDate();
        int lockIndex = Math.floorMod(Objects.hash(event.sensorId(), day), LOCK_STRIPES);
        // Le listener et la reprise des messages en attente peuvent traiter le
        // même capteur/jour en parallèle. La séquence complète doit rester ordonnée
        // pour éviter qu'un ancien recalcul écrase un agrégat plus récent.
        synchronized (dayLocks[lockIndex]) {
            cassandraService.save(event);
            try {
                dailySummaryService.computeAndStore(event.sensorId(), day);
            } catch (SensorNotFoundException exception) {
                // Le Collector accepte les mesures sans métadonnées préalables.
                // La série brute reste disponible pour ce capteur non enregistré.
                log.info("Résumé ignoré pour le capteur non enregistré '{}': {}", event.sensorId(), exception.getMessage());
            }
            persistedCount.incrementAndGet();

            if (recentEvents.size() >= MAX_RECENT) {
                recentEvents.remove(0);
            }
            recentEvents.add(event);
        }
    }

    @Override
    public List<EnergyMeasurementEvent> getPersistedEvents() {
        return Collections.unmodifiableList(new ArrayList<>(recentEvents));
    }

    @Override
    public long getPersistedCount() {
        return persistedCount.get();
    }

    @Override
    public void clear() {
        recentEvents.clear();
        log.warn("Cache des événements récents vidé (les données restent dans Cassandra).");
    }
}
