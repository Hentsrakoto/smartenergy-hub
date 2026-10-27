package com.smartenergy.storage.cassandra.entity;

import org.springframework.data.cassandra.core.cql.Ordering;
import org.springframework.data.cassandra.core.cql.PrimaryKeyType;
import org.springframework.data.cassandra.core.mapping.CassandraType;
import org.springframework.data.cassandra.core.mapping.PrimaryKeyClass;
import org.springframework.data.cassandra.core.mapping.PrimaryKeyColumn;

import java.io.Serializable;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;

/**
 * Clé primaire composite Cassandra de la table {@code measurements_by_sensor_day}.
 *
 * <p>Cette classe matérialise exactement la clé déclarée dans
 * {@code scripts/init_cassandra.cql} :</p>
 *
 * <pre>
 * PRIMARY KEY ((sensor_id, day), measured_at)
 * WITH CLUSTERING ORDER BY (measured_at DESC)
 * </pre>
 *
 * <ul>
 *   <li>{@code sensor_id} et {@code day} forment la clé de partition ;</li>
 *   <li>{@code measured_at} est la clé de clustering, triée en ordre décroissant.</li>
 * </ul>
 */
@PrimaryKeyClass
public class MeasurementKey implements Serializable {

    private static final long serialVersionUID = 1L;

    @PrimaryKeyColumn(name = "sensor_id", type = PrimaryKeyType.PARTITIONED, ordinal = 0)
    private String sensorId;

    @PrimaryKeyColumn(name = "day", type = PrimaryKeyType.PARTITIONED, ordinal = 1)
    @CassandraType(type = CassandraType.Name.DATE)
    private LocalDate day;

    @PrimaryKeyColumn(name = "measured_at", type = PrimaryKeyType.CLUSTERED,
            ordering = Ordering.DESCENDING, ordinal = 0)
    @CassandraType(type = CassandraType.Name.TIMESTAMP)
    private Instant measuredAt;

    /** Constructeur sans argument requis par Spring Data Cassandra. */
    public MeasurementKey() {
    }

    public MeasurementKey(String sensorId, LocalDate day, Instant measuredAt) {
        this.sensorId = sensorId;
        this.day = day;
        this.measuredAt = measuredAt;
    }

    public String getSensorId() {
        return sensorId;
    }

    public void setSensorId(String sensorId) {
        this.sensorId = sensorId;
    }

    public LocalDate getDay() {
        return day;
    }

    public void setDay(LocalDate day) {
        this.day = day;
    }

    public Instant getMeasuredAt() {
        return measuredAt;
    }

    public void setMeasuredAt(Instant measuredAt) {
        this.measuredAt = measuredAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof MeasurementKey that)) {
            return false;
        }
        return Objects.equals(sensorId, that.sensorId)
                && Objects.equals(day, that.day)
                && Objects.equals(measuredAt, that.measuredAt);
    }

    @Override
    public int hashCode() {
        return Objects.hash(sensorId, day, measuredAt);
    }

    @Override
    public String toString() {
        return "MeasurementKey{sensorId='" + sensorId + "', day=" + day +
               ", measuredAt=" + measuredAt + '}';
    }
}
