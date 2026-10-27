package com.smartenergy.storage.mongo.document;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/**
 * Document MongoDB représentant l'agrégat quotidien d'un capteur.
 *
 * <p>L'identifiant technique ({@code _id}) est construit sous la forme
 * {@code sensorId:date ISO}. Il garantit l'unicité de l'agrégat et rend les
 * réécritures idempotentes (upsert sur le même identifiant).</p>
 *
 * <p>Collection : {@code daily_summaries}.</p>
 */
@Document(collection = "daily_summaries")
@CompoundIndex(name = "sensor_date_idx", def = "{'sensorId': 1, 'date': 1}", unique = true)
public class DailySummaryDocument {

    @Id
    private String id;

    private String sensorId;

    /** Date au format ISO-8601 {@code YYYY-MM-DD}, conforme au contrat JSON du guide. */
    private String date;

    private long measurementCount;

    private double minPowerKw;

    private double avgPowerKw;

    private double maxPowerKw;

    private double energyKwh;

    private long anomalyCount;

    private Instant updatedAt;

    public DailySummaryDocument() {
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getSensorId() {
        return sensorId;
    }

    public void setSensorId(String sensorId) {
        this.sensorId = sensorId;
    }

    public String getDate() {
        return date;
    }

    public void setDate(String date) {
        this.date = date;
    }

    public long getMeasurementCount() {
        return measurementCount;
    }

    public void setMeasurementCount(long measurementCount) {
        this.measurementCount = measurementCount;
    }

    public double getMinPowerKw() {
        return minPowerKw;
    }

    public void setMinPowerKw(double minPowerKw) {
        this.minPowerKw = minPowerKw;
    }

    public double getAvgPowerKw() {
        return avgPowerKw;
    }

    public void setAvgPowerKw(double avgPowerKw) {
        this.avgPowerKw = avgPowerKw;
    }

    public double getMaxPowerKw() {
        return maxPowerKw;
    }

    public void setMaxPowerKw(double maxPowerKw) {
        this.maxPowerKw = maxPowerKw;
    }

    public double getEnergyKwh() {
        return energyKwh;
    }

    public void setEnergyKwh(double energyKwh) {
        this.energyKwh = energyKwh;
    }

    public long getAnomalyCount() {
        return anomalyCount;
    }

    public void setAnomalyCount(long anomalyCount) {
        this.anomalyCount = anomalyCount;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
