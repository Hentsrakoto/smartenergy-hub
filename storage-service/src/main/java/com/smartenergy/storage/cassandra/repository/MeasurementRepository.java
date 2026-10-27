package com.smartenergy.storage.cassandra.repository;

import com.smartenergy.storage.cassandra.entity.MeasurementEntity;
import com.smartenergy.storage.cassandra.entity.MeasurementKey;
import org.springframework.data.cassandra.repository.CassandraRepository;
import org.springframework.data.cassandra.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Repository Spring Data Cassandra pour les mesures brutes.
 *
 * <p>Toutes les requêtes sont orientées vers la clé de partition {@code (sensor_id, day)}
 * pour garantir des lectures efficaces sans full scan.</p>
 */
@Repository
public interface MeasurementRepository extends CassandraRepository<MeasurementEntity, MeasurementKey> {

    /**
     * Récupère l'historique d'un capteur pour un jour donné, trié par timestamp décroissant.
     *
     * @param sensorId identifiant du capteur
     * @param day      jour de la partition (UTC)
     * @return liste ordonnée du plus récent au plus ancien
     */
    @Query("SELECT * FROM measurements_by_sensor_day WHERE sensor_id = ?0 AND day = ?1")
    List<MeasurementEntity> findBySensorIdAndDay(String sensorId, LocalDate day);

    /**
     * Récupère les mesures d'un capteur pour un jour entre deux timestamps.
     *
     * @param sensorId identifiant du capteur
     * @param day      jour de la partition
     * @param from     borne inférieure incluse (UTC)
     * @param to       borne supérieure incluse (UTC)
     * @return liste ordonnée du plus récent au plus ancien
     */
    @Query("SELECT * FROM measurements_by_sensor_day WHERE sensor_id = ?0 AND day = ?1 AND measured_at >= ?2 AND measured_at <= ?3")
    List<MeasurementEntity> findBySensorIdAndDayAndMeasuredAtBetween(
            String sensorId, LocalDate day, Instant from, Instant to);

    /**
     * Dernière mesure connue d'un capteur pour un jour (LIMIT 1, clustering DESC).
     *
     * @param sensorId identifiant du capteur
     * @param day      jour de la partition
     * @return mesure la plus récente si présente
     */
    @Query("SELECT * FROM measurements_by_sensor_day WHERE sensor_id = ?0 AND day = ?1 LIMIT 1")
    Optional<MeasurementEntity> findLatestBySensorIdAndDay(String sensorId, LocalDate day);
}
