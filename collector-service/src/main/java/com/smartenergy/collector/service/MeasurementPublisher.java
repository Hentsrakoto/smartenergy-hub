package com.smartenergy.collector.service;

import com.smartenergy.collector.dto.EnergyMeasurementRequest;
import org.springframework.data.redis.connection.stream.RecordId;

/**
 * Interface de publication asynchrone des télémesures dans le système de streaming.
 */
public interface MeasurementPublisher {

    /**
     * Publie la mesure énergétique dans le flux de messages.
     *
     * @param request La mesure énergétique validée
     * @return L'identifiant du message généré par le broker de streaming
     */
    RecordId publish(EnergyMeasurementRequest request);
}
