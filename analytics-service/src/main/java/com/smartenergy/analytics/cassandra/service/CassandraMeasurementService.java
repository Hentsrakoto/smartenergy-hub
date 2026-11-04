package com.smartenergy.analytics.cassandra.service;

import com.smartenergy.analytics.cassandra.entity.MeasurementEntity;
import com.smartenergy.analytics.cassandra.repository.MeasurementRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/** Lecture seule des partitions de mesures Cassandra. */
@Service
public class CassandraMeasurementService {

    private static final Logger log = LoggerFactory.getLogger(CassandraMeasurementService.class);

    private final MeasurementRepository repository;

    public CassandraMeasurementService(MeasurementRepository repository) {
        this.repository = repository;
    }

    /**
     * Récupère l'historique d'un capteur pour un jour donné.
     *
     * @param sensorId identifiant du capteur
     * @param day      jour (UTC)
     * @return liste de mesures du plus récent au plus ancien
     */
    public List<MeasurementEntity> findByDay(String sensorId, LocalDate day) {
        return repository.findBySensorIdAndDay(sensorId, day);
    }

    /**
     * Récupère l'historique d'un capteur entre deux timestamps.
     * Si la plage couvre plusieurs jours, effectue une requête par jour.
     *
     * @param sensorId identifiant du capteur
     * @param from     borne inférieure incluse (UTC)
     * @param to       borne supérieure incluse (UTC)
     * @return liste de mesures, triée chronologiquement de la plus récente à la plus ancienne
     */
    public List<MeasurementEntity> findByRange(String sensorId, Instant from, Instant to) {
        if (sensorId == null || sensorId.isBlank()) {
            throw new IllegalArgumentException("sensorId ne doit pas être vide");
        }
        if (from == null || to == null) {
            throw new IllegalArgumentException("Les bornes 'from' et 'to' sont obligatoires");
        }
        if (from.isAfter(to)) {
            throw new IllegalArgumentException("'from' doit être antérieur ou égal à 'to'");
        }
        if (java.time.Duration.between(from, to).compareTo(java.time.Duration.ofDays(31)) > 0) {
            throw new IllegalArgumentException("La plage ne doit pas dépasser 31 jours");
        }

        LocalDate dayFrom = from.atZone(ZoneOffset.UTC).toLocalDate();
        LocalDate dayTo   = to.atZone(ZoneOffset.UTC).toLocalDate();

        List<MeasurementEntity> results = new ArrayList<>();
        LocalDate current = dayFrom;
        while (!current.isAfter(dayTo)) {
            List<MeasurementEntity> partial = repository.findBySensorIdAndDayAndMeasuredAtBetween(
                    sensorId, current, from, to);
            results.addAll(partial);
            current = current.plusDays(1);
        }

        // Chaque partition journalière est déjà triée DESC par Cassandra.
        // On garantit ici un ordre global décroissant lorsque la plage couvre plusieurs jours.
        results.sort(Comparator.comparing(MeasurementEntity::getMeasuredAt).reversed());

        log.debug("Historique récupéré: sensorId='{}', de {} à {} -> {} mesures",
                sensorId, from, to, results.size());
        return results;
    }

    /**
     * Retourne la dernière mesure connue d'un capteur pour un jour donné.
     *
     * @param sensorId identifiant du capteur
     * @param day      jour (UTC)
     * @return mesure la plus récente si présente
     */
    public Optional<MeasurementEntity> findLatest(String sensorId, LocalDate day) {
        return repository.findLatestBySensorIdAndDay(sensorId, day);
    }
}
