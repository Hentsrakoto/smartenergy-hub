package com.smartenergy.storage.mongo.document;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Document MongoDB représentant les métadonnées et la configuration d'un capteur.
 *
 * <p>L'identifiant MongoDB ({@code _id}) est le {@code sensorId} métier. Il est ainsi
 * identique à celui utilisé dans Redis et Cassandra, ce qui garantit la cohérence
 * inter-bases et permet un accès direct par identifiant.</p>
 *
 * <p>Collection : {@code sensors}.</p>
 */
@Document(collection = "sensors")
public class SensorDocument {

    @Id
    private String sensorId;

    private String name;

    private String building;

    private Integer floor;

    /** Seuil de puissance utilisé pour la détection d'anomalie. */
    private double maxPowerKw = 5_000.0;

    private boolean active = true;

    private List<String> tags = new ArrayList<>();

    /** Bornes de tension utilisées pour la détection d'anomalie. */
    private double minVoltage = 150.0;
    private double maxVoltage = 300.0;

    /** Seuil de température utilisé pour la détection d'anomalie. */
    private double maxTemperature = 125.0;

    /** Intervalle de mesure configuré, utilisé pour l'estimation d'énergie. */
    private int samplingIntervalSeconds = 60;

    private Instant createdAt;
    private Instant updatedAt;

    public SensorDocument() {
    }

    public String getSensorId() {
        return sensorId;
    }

    public void setSensorId(String sensorId) {
        this.sensorId = sensorId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getBuilding() {
        return building;
    }

    public void setBuilding(String building) {
        this.building = building;
    }

    public Integer getFloor() {
        return floor;
    }

    public void setFloor(Integer floor) {
        this.floor = floor;
    }

    public double getMaxPowerKw() {
        return maxPowerKw;
    }

    public void setMaxPowerKw(double maxPowerKw) {
        this.maxPowerKw = maxPowerKw;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public List<String> getTags() {
        return tags;
    }

    public void setTags(List<String> tags) {
        this.tags = tags;
    }

    public double getMinVoltage() {
        return minVoltage;
    }

    public void setMinVoltage(double minVoltage) {
        this.minVoltage = minVoltage;
    }

    public double getMaxVoltage() {
        return maxVoltage;
    }

    public void setMaxVoltage(double maxVoltage) {
        this.maxVoltage = maxVoltage;
    }

    public double getMaxTemperature() {
        return maxTemperature;
    }

    public void setMaxTemperature(double maxTemperature) {
        this.maxTemperature = maxTemperature;
    }

    public int getSamplingIntervalSeconds() {
        return samplingIntervalSeconds;
    }

    public void setSamplingIntervalSeconds(int samplingIntervalSeconds) {
        this.samplingIntervalSeconds = samplingIntervalSeconds;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
