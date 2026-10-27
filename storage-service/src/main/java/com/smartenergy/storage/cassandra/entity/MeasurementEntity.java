package com.smartenergy.storage.cassandra.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.data.cassandra.core.mapping.Column;
import org.springframework.data.cassandra.core.mapping.PrimaryKey;
import org.springframework.data.cassandra.core.mapping.Table;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;

/**
 * Entité Cassandra mappant la table {@code smartenergy.measurements_by_sensor_day}.
 *
 * <p>Clé de partition : {@code (sensor_id, day)}<br>
 * Clustering key : {@code measured_at DESC}
 *
 * <p>Justification du schéma :
 * <ul>
 *   <li>La requête principale est l'historique d'un capteur entre deux dates.
 *   <li>Partitionner par {@code (sensor_id, day)} groupe toutes les mesures d'un capteur
 *       pour une journée dans une partition unique → lectures séquentielles rapides.
 *   <li>Le clustering décroissant permet de récupérer les dernières mesures en LIMIT 1
 *       sans tri applicatif.
 * </ul>
 */
@Table("measurements_by_sensor_day")
public class MeasurementEntity {

    /**
     * Clé primaire composite. Elle est masquée de la sérialisation JSON pour exposer
     * une représentation plate et lisible via l'API REST (les getters délégués ci-dessous
     * fournissent {@code sensorId}, {@code day} et {@code measuredAt}).
     */
    @PrimaryKey
    @JsonIgnore
    private MeasurementKey key;

    @Column("power_kw")
    private double powerKw;

    @Column("voltage")
    private double voltage;

    @Column("temperature")
    private Double temperature;

    // Constructeur sans argument requis par Spring Data Cassandra
    public MeasurementEntity() {
    }

    public MeasurementEntity(String sensorId, LocalDate day, Instant measuredAt,
                             double powerKw, double voltage, Double temperature) {
        this.key = new MeasurementKey(sensorId, day, measuredAt);
        this.powerKw = powerKw;
        this.voltage = voltage;
        this.temperature = temperature;
    }

    // --- Getters & Setters ---

    @JsonIgnore
    public MeasurementKey getKey() {
        return key;
    }

    public void setKey(MeasurementKey key) {
        this.key = key;
    }

    @JsonProperty("sensorId")
    public String getSensorId() {
        return key != null ? key.getSensorId() : null;
    }

    @JsonProperty("day")
    public LocalDate getDay() {
        return key != null ? key.getDay() : null;
    }

    @JsonProperty("measuredAt")
    public Instant getMeasuredAt() {
        return key != null ? key.getMeasuredAt() : null;
    }

    public double getPowerKw() {
        return powerKw;
    }

    public void setPowerKw(double powerKw) {
        this.powerKw = powerKw;
    }

    public double getVoltage() {
        return voltage;
    }

    public void setVoltage(double voltage) {
        this.voltage = voltage;
    }

    public Double getTemperature() {
        return temperature;
    }

    public void setTemperature(Double temperature) {
        this.temperature = temperature;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof MeasurementEntity that)) {
            return false;
        }
        return Double.compare(powerKw, that.powerKw) == 0
                && Double.compare(voltage, that.voltage) == 0
                && Objects.equals(key, that.key)
                && Objects.equals(temperature, that.temperature);
    }

    @Override
    public int hashCode() {
        return Objects.hash(key, powerKw, voltage, temperature);
    }

    @Override
    public String toString() {
        return "MeasurementEntity{" + key +
               ", powerKw=" + powerKw +
               ", voltage=" + voltage +
               ", temperature=" + temperature + '}';
    }
}
