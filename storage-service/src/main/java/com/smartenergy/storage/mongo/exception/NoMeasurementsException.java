package com.smartenergy.storage.mongo.exception;

/**
 * Levée lorsqu'aucune mesure n'existe dans Cassandra pour un capteur et une date donnés.
 */
public class NoMeasurementsException extends RuntimeException {

    public NoMeasurementsException(String sensorId, String date) {
        super("Aucune mesure trouvée pour le capteur '" + sensorId + "' à la date " + date);
    }
}
