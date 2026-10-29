package com.smartenergy.storage.mongo.document;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/** Instantané métier quotidien, distinct de l'agrégat de calcul. */
@Document(collection = "reports")
public class DailyReportDocument {
    @Id private String id;
    private String sensorId;
    private String sensorName;
    private String date;
    private long measurementCount;
    private double energyKwh;
    private long anomalyCount;
    private String status;
    private Instant generatedAt;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getSensorId() { return sensorId; }
    public void setSensorId(String sensorId) { this.sensorId = sensorId; }
    public String getSensorName() { return sensorName; }
    public void setSensorName(String sensorName) { this.sensorName = sensorName; }
    public String getDate() { return date; }
    public void setDate(String date) { this.date = date; }
    public long getMeasurementCount() { return measurementCount; }
    public void setMeasurementCount(long measurementCount) { this.measurementCount = measurementCount; }
    public double getEnergyKwh() { return energyKwh; }
    public void setEnergyKwh(double energyKwh) { this.energyKwh = energyKwh; }
    public long getAnomalyCount() { return anomalyCount; }
    public void setAnomalyCount(long anomalyCount) { this.anomalyCount = anomalyCount; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Instant getGeneratedAt() { return generatedAt; }
    public void setGeneratedAt(Instant generatedAt) { this.generatedAt = generatedAt; }
}
