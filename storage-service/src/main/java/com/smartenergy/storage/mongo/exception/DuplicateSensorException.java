package com.smartenergy.storage.mongo.exception;

/**
 * Levée lorsqu'un capteur avec le même identifiant existe déjà.
 */
public class DuplicateSensorException extends RuntimeException {

    public DuplicateSensorException(String sensorId) {
        super("Un capteur avec l'identifiant '" + sensorId + "' existe déjà");
    }
}
