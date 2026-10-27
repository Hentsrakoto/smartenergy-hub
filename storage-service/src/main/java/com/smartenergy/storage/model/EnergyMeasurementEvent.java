package com.smartenergy.storage.model;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Map;
import java.util.Objects;

/**
 * Modèle représentant un événement de mesure consommé depuis Redis Streams.
 */
public record EnergyMeasurementEvent(
        String recordId,
        String sensorId,
        Instant timestamp,
        double powerKw,
        double voltage,
        Double temperature
) {
    public EnergyMeasurementEvent {
        Objects.requireNonNull(recordId, "recordId ne doit pas être nul");
        Objects.requireNonNull(sensorId, "sensorId ne doit pas être nul");
        Objects.requireNonNull(timestamp, "timestamp ne doit pas être nul");
        if (!sensorId.matches("^[a-zA-Z0-9_-]{3,50}$")) {
            throw new IllegalArgumentException("sensorId doit contenir 3 à 50 caractères alphanumériques, tirets ou underscores");
        }
        if (!Double.isFinite(powerKw) || powerKw < 0.0 || powerKw > 5000.0) {
            throw new IllegalArgumentException("powerKw ne peut pas être négatif ou dépasser 5000 kW: " + powerKw);
        }
        if (!Double.isFinite(voltage) || voltage < 150.0 || voltage > 300.0) {
            throw new IllegalArgumentException("voltage doit être compris entre 150 et 300 V: " + voltage);
        }
        if (temperature != null && (!Double.isFinite(temperature) || temperature < -40.0 || temperature > 125.0)) {
            throw new IllegalArgumentException("temperature doit être comprise entre -40 et 125 °C: " + temperature);
        }
    }

    /**
     * Reconstitue un événement à partir de l'identifiant du message Redis et de la Map de valeurs.
     */
    public static EnergyMeasurementEvent fromMap(String recordId, Map<Object, Object> fields) {
        if (fields == null || fields.isEmpty()) {
            throw new IllegalArgumentException("Les champs de l'événement Redis ne peuvent pas être vides");
        }

        String sensorId = getString(fields, "sensorId");
        if (sensorId == null || sensorId.isBlank()) {
            throw new IllegalArgumentException("Champ 'sensorId' manquant ou vide");
        }

        String rawTimestamp = getString(fields, "timestamp");
        if (rawTimestamp == null || rawTimestamp.isBlank()) {
            throw new IllegalArgumentException("Champ 'timestamp' manquant ou vide");
        }
        Instant timestamp;
        try {
            timestamp = Instant.parse(rawTimestamp);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("Format d'horodatage invalide: " + rawTimestamp, e);
        }

        String rawPower = getString(fields, "powerKw");
        if (rawPower == null || rawPower.isBlank()) {
            throw new IllegalArgumentException("Champ 'powerKw' manquant");
        }
        double powerKw;
        try {
            powerKw = Double.parseDouble(rawPower);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Valeur 'powerKw' numérique invalide: " + rawPower, e);
        }

        String rawVoltage = getString(fields, "voltage");
        if (rawVoltage == null || rawVoltage.isBlank()) {
            throw new IllegalArgumentException("Champ 'voltage' manquant");
        }
        double voltage;
        try {
            voltage = Double.parseDouble(rawVoltage);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Valeur 'voltage' numérique invalide: " + rawVoltage, e);
        }

        Double temperature = null;
        String rawTemperature = getString(fields, "temperature");
        if (rawTemperature != null && !rawTemperature.isBlank()) {
            try {
                temperature = Double.parseDouble(rawTemperature);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Valeur 'temperature' numérique invalide: " + rawTemperature, e);
            }
        }

        return new EnergyMeasurementEvent(recordId, sensorId, timestamp, powerKw, voltage, temperature);
    }

    private static String getString(Map<Object, Object> map, String key) {
        Object val = map.get(key);
        return val != null ? val.toString() : null;
    }
}
