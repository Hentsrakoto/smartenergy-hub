package com.smartenergy.storage.service;

import com.smartenergy.storage.cassandra.entity.MeasurementEntity;
import com.smartenergy.storage.cassandra.service.CassandraMeasurementService;
import com.smartenergy.storage.mongo.repository.DailySummaryRepository;
import com.smartenergy.storage.mongo.service.SensorService;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.Optional;

/** Lecture ciblée de la dernière mesure d'un capteur enregistré. */
@Service
public class LatestMeasurementService {

    private final DailySummaryRepository summaries;
    private final CassandraMeasurementService cassandra;
    private final SensorService sensors;

    public LatestMeasurementService(DailySummaryRepository summaries,
                                    CassandraMeasurementService cassandra,
                                    SensorService sensors) {
        this.summaries = summaries;
        this.cassandra = cassandra;
        this.sensors = sensors;
    }

    public Optional<MeasurementEntity> findLatestKnown(String sensorId) {
        sensors.findDocument(sensorId);
        return summaries.findFirstBySensorIdOrderByDateDesc(sensorId)
                .flatMap(summary -> cassandra.findLatest(sensorId, LocalDate.parse(summary.getDate())));
    }
}
