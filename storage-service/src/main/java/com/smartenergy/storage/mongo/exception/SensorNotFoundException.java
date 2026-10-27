package com.smartenergy.storage.mongo.exception;

/**
 * Levée lorsqu'un capteur demandé n'existe pas dans MongoDB.
 */
public class SensorNotFoundException extends RuntimeException {

    public SensorNotFoundException(String sensorId) {
        super("Capteur introuvable : " + sensorId);
    }
}
