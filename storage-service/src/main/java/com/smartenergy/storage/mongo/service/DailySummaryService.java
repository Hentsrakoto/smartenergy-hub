package com.smartenergy.storage.mongo.service;

import com.smartenergy.storage.cassandra.entity.MeasurementEntity;
import com.smartenergy.storage.cassandra.service.CassandraMeasurementService;
import com.smartenergy.storage.mongo.document.DailySummaryDocument;
import com.smartenergy.storage.mongo.document.SensorDocument;
import com.smartenergy.storage.mongo.dto.DailySummaryResponse;
import com.smartenergy.storage.mongo.exception.NoMeasurementsException;
import com.smartenergy.storage.mongo.repository.DailySummaryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
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

    /**
     * Recalcule et persiste l'agrégat d'un capteur pour une date.
     * Méthode réutilisable par le Storage Service lors du traitement des mesures.
     */
    public DailySummaryDocument computeAndStore(String sensorId, LocalDate date) {
        if (date == null) {
            throw new IllegalArgumentException("date est obligatoire");
        }
        return computeAndStore(sensorService.findDocument(sensorId), date);
    }

    private DailySummaryDocument computeAndStore(SensorDocument sensor, LocalDate date) {
        List<MeasurementEntity> measurements =
                cassandraService.findByDay(sensor.getSensorId(), date);

        if (measurements == null || measurements.isEmpty()) {
            throw new NoMeasurementsException(sensor.getSensorId(), date.toString());
        }

        List<MeasurementEntity> sorted = new ArrayList<>(measurements);
        sorted.sort(Comparator.comparing(MeasurementEntity::getMeasuredAt));

        long count = sorted.size();
        double sum = 0.0;
        double min = Double.POSITIVE_INFINITY;
        double max = Double.NEGATIVE_INFINITY;
        long anomalyCount = 0;

        for (MeasurementEntity measurement : sorted) {
            sum += measurement.getPowerKw();
            min = Math.min(min, measurement.getPowerKw());
            max = Math.max(max, measurement.getPowerKw());
            if (isAnomaly(measurement, sensor)) {
                anomalyCount++;
            }
        }

        double average = sum / count;
        double energyKwh = computeEnergyKwh(sorted, sensor.getSamplingIntervalSeconds(),
                date.plusDays(1).atStartOfDay(java.time.ZoneOffset.UTC).toInstant());

        DailySummaryDocument summary = new DailySummaryDocument();
        summary.setId(sensor.getSensorId() + ":" + date);
        summary.setSensorId(sensor.getSensorId());
        summary.setDate(date.toString());
        summary.setMeasurementCount(count);
        summary.setMinPowerKw(round(min));
        summary.setAvgPowerKw(round(average));
        summary.setMaxPowerKw(round(max));
        summary.setEnergyKwh(round(energyKwh));
        summary.setAnomalyCount(anomalyCount);
        summary.setUpdatedAt(Instant.now());

        summaryRepository.save(summary);

        log.info("Agrégat quotidien enregistré dans MongoDB: sensorId='{}', date={}, mesures={}, énergie={} kWh, anomalies={}",
                sensor.getSensorId(), date, count, summary.getEnergyKwh(), anomalyCount);

        return summary;
    }

    private boolean isAnomaly(MeasurementEntity measurement, SensorDocument sensor) {
        if (measurement.getPowerKw() > sensor.getMaxPowerKw()) {
            return true;
        }
        if (measurement.getVoltage() < sensor.getMinVoltage()
                || measurement.getVoltage() > sensor.getMaxVoltage()) {
            return true;
        }
        Double temperature = measurement.getTemperature();
        return temperature != null && temperature > sensor.getMaxTemperature();
    }

    private double computeEnergyKwh(List<MeasurementEntity> sorted, int samplingIntervalSeconds, Instant dayEnd) {
        double energy = 0.0;
        int fallbackInterval = Math.max(1, samplingIntervalSeconds);

        for (int i = 0; i < sorted.size(); i++) {
            Instant start = sorted.get(i).getMeasuredAt();
            Instant end = (i + 1 < sorted.size())
                    ? sorted.get(i + 1).getMeasuredAt()
                    : start.plusSeconds(fallbackInterval);
            if (end.isAfter(dayEnd)) {
                end = dayEnd;
            }

            long millis = Duration.between(start, end).toMillis();
            if (millis <= 0) {
                continue;
            }

            double hours = millis / 3_600_000.0;
            // Plafonne un intervalle aberrant à 24 h pour éviter une énergie irréaliste.
            energy += sorted.get(i).getPowerKw() * Math.min(hours, 24.0);
        }

        return energy;
    }

    private double round(double value) {
        return BigDecimal.valueOf(value).setScale(6, RoundingMode.HALF_UP).doubleValue();
    }
}
