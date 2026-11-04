package com.smartenergy.analytics.mongo.service;

import com.smartenergy.analytics.cassandra.entity.MeasurementEntity;
import com.smartenergy.analytics.cassandra.service.CassandraMeasurementService;
import com.smartenergy.analytics.mongo.document.DailySummaryDocument;
import com.smartenergy.analytics.mongo.document.SensorDocument;
import com.smartenergy.analytics.mongo.dto.DailySummaryResponse;
import com.smartenergy.analytics.mongo.exception.NoMeasurementsException;
import com.smartenergy.analytics.mongo.repository.DailySummaryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Service de calcul et de stockage des agrégats quotidiens dans MongoDB.
 *
 * <p>Le calcul est effectué à partir des mesures brutes lues dans Cassandra, puis
 * le résultat est persisté dans la collection {@code daily_summaries}. L'agrégat est
 * donc consultable indépendamment de Cassandra via l'API des capteurs.</p>
 *
 * <p>Formule d'énergie : {@code E = Σ(P_i × Δt_i)} où {@code Δt_i} est l'intervalle
 * entre la mesure {@code i} et la suivante (ou l'intervalle d'échantillonnage configuré
 * pour la dernière mesure), exprimé en heures. Le résultat est en kWh.</p>
 */
@Service
public class DailySummaryService {

    private static final Logger log = LoggerFactory.getLogger(DailySummaryService.class);

    private final Object[] locks = java.util.stream.IntStream.range(0, 256).mapToObj(i -> new Object()).toArray();

    private final DailySummaryRepository summaryRepository;
    private final SensorService sensorService;
    private final CassandraMeasurementService cassandraService;

    public DailySummaryService(DailySummaryRepository summaryRepository,
                               SensorService sensorService,
                               CassandraMeasurementService cassandraService) {
        this.summaryRepository = summaryRepository;
        this.sensorService = sensorService;
        this.cassandraService = cassandraService;
    }

    /**
     * Retourne l'agrégat stocké, ou le calcule depuis Cassandra s'il est absent.
     *
     * @param sensorId identifiant du capteur
     * @param date     date du résumé
     * @param refresh  si vrai, force le recalcul même si un agrégat existe déjà
     */
    public DailySummaryResponse getOrCompute(String sensorId, LocalDate date, boolean refresh) {
        if (date == null) {
            throw new IllegalArgumentException("date est obligatoire");
        }

        synchronized (lock(sensorId, date)) {
            SensorDocument sensor = sensorService.findDocument(sensorId);
            if (!refresh) {
                Optional<DailySummaryDocument> existing =
                        summaryRepository.findBySensorIdAndDate(sensor.getSensorId(), date.toString());
                if (existing.isPresent()) {
                    return DailySummaryResponse.from(existing.get());
                }
            }
            return DailySummaryResponse.from(computeAndStore(sensor, date));
        }
    }

    /**
     * Recalcule et persiste l'agrégat d'un capteur pour une date.
     * Point d’entrée interne soumis au même verrou que les recalculs à la demande.
     */
    public DailySummaryDocument computeAndStore(String sensorId, LocalDate date) {
        if (date == null) {
            throw new IllegalArgumentException("date est obligatoire");
        }
        synchronized (lock(sensorId, date)) {
            return computeAndStore(sensorService.findDocument(sensorId), date);
        }
    }

    private DailySummaryDocument computeAndStore(SensorDocument sensor, LocalDate date) {
        List<MeasurementEntity> measurements =
                cassandraService.findByDay(sensor.getSensorId(), date);

        if (measurements == null || measurements.isEmpty()) {
            throw new NoMeasurementsException(sensor.getSensorId(), date.toString());
        }

        var calculated = com.smartenergy.analytics.service.MeasurementCalculator.summarize(sensor, measurements,
                date.atStartOfDay(java.time.ZoneOffset.UTC).toInstant(),
                date.plusDays(1).atStartOfDay(java.time.ZoneOffset.UTC).toInstant());

        DailySummaryDocument summary = new DailySummaryDocument();
        summary.setId(sensor.getSensorId() + ":" + date);
        summary.setSensorId(sensor.getSensorId());
        summary.setDate(date.toString());
        summary.setMeasurementCount(calculated.measurementCount());
        summary.setMinPowerKw(calculated.minPowerKw());
        summary.setAvgPowerKw(calculated.avgPowerKw());
        summary.setMaxPowerKw(calculated.maxPowerKw());
        summary.setEnergyKwh(calculated.energyKwh());
        summary.setAnomalyCount(calculated.anomalyCount());
        summary.setUpdatedAt(Instant.now());

        summaryRepository.save(summary);

        log.info("Agrégat quotidien enregistré dans MongoDB: sensorId='{}', date={}, mesures={}, énergie={} kWh, anomalies={}",
                sensor.getSensorId(), date, calculated.measurementCount(), summary.getEnergyKwh(), calculated.anomalyCount());

        return summary;
    }

    // Verrous stables et bornés en mémoire : même capteur/jour, même verrou.
    // Le verrou couvre lecture Cassandra, calcul et écriture Mongo ; une seule réplique.
    private Object lock(String sensorId, LocalDate date) {
        return locks[Math.floorMod(java.util.Objects.hash(sensorId, date), locks.length)];
    }
}
