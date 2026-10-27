package com.smartenergy.storage.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EnergyMeasurementEventTest {

    @Test
    @DisplayName("Doit désérialiser un événement complet valide")
    void shouldParseValidEvent() {
        Map<Object, Object> fields = Map.of(
                "sensorId", "sensor-001",
                "timestamp", "2026-10-21T12:00:00Z",
                "powerKw", "42.5",
                "voltage", "230.1",
                "temperature", "25.4"
        );

        EnergyMeasurementEvent event = EnergyMeasurementEvent.fromMap("1001-0", fields);

        assertThat(event.recordId()).isEqualTo("1001-0");
        assertThat(event.sensorId()).isEqualTo("sensor-001");
        assertThat(event.timestamp()).isEqualTo(Instant.parse("2026-10-21T12:00:00Z"));
        assertThat(event.powerKw()).isEqualTo(42.5);
        assertThat(event.voltage()).isEqualTo(230.1);
        assertThat(event.temperature()).isEqualTo(25.4);
    }

    @Test
    @DisplayName("Doit accepter un événement sans température (optionnelle)")
    void shouldParseValidEventWithoutTemperature() {
        Map<Object, Object> fields = Map.of(
                "sensorId", "sensor-002",
                "timestamp", "2026-10-21T12:00:00Z",
                "powerKw", "10.0",
                "voltage", "228.0"
        );

        EnergyMeasurementEvent event = EnergyMeasurementEvent.fromMap("1002-0", fields);

        assertThat(event.sensorId()).isEqualTo("sensor-002");
        assertThat(event.temperature()).isNull();
    }

    @Test
    @DisplayName("Doit rejeter un événement avec puissance négative")
    void shouldRejectNegativePower() {
        Map<Object, Object> fields = Map.of(
                "sensorId", "sensor-001",
                "timestamp", "2026-10-21T12:00:00Z",
                "powerKw", "-5.0",
                "voltage", "230.0"
        );

        assertThatThrownBy(() -> EnergyMeasurementEvent.fromMap("1003-0", fields))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("powerKw ne peut pas être négatif");
    }

    @Test
    @DisplayName("Doit rejeter un événement avec tension négative")
    void shouldRejectNegativeVoltage() {
        Map<Object, Object> fields = Map.of(
                "sensorId", "sensor-001",
                "timestamp", "2026-10-21T12:00:00Z",
                "powerKw", "15.0",
                "voltage", "-10.0"
        );

        assertThatThrownBy(() -> EnergyMeasurementEvent.fromMap("1004-0", fields))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("voltage doit être compris");
    }

    @Test
    @DisplayName("Doit rejeter un événement avec sensorId vide")
    void shouldRejectBlankSensorId() {
        Map<Object, Object> fields = Map.of(
                "sensorId", "   ",
                "timestamp", "2026-10-21T12:00:00Z",
                "powerKw", "15.0",
                "voltage", "230.0"
        );

        assertThatThrownBy(() -> EnergyMeasurementEvent.fromMap("1005-0", fields))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("sensorId");
    }

    @Test
    @DisplayName("Doit rejeter un format d'horodatage invalide")
    void shouldRejectInvalidTimestamp() {
        Map<Object, Object> fields = Map.of(
                "sensorId", "sensor-001",
                "timestamp", "21/10/2026 12:00",
                "powerKw", "15.0",
                "voltage", "230.0"
        );

        assertThatThrownBy(() -> EnergyMeasurementEvent.fromMap("1006-0", fields))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Format d'horodatage invalide");
    }

    @Test
    void shouldRejectNonFiniteAndOutOfRangeValues() {
        Map<Object, Object> fields = new HashMap<>(Map.of(
                "sensorId", "sensor-001", "timestamp", "2026-10-21T12:00:00Z",
                "powerKw", "15.0", "voltage", "230.0"));
        fields.put("powerKw", "NaN");
        assertThatThrownBy(() -> EnergyMeasurementEvent.fromMap("1007-0", fields))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("powerKw");
        fields.put("powerKw", "15.0");
        fields.put("voltage", "Infinity");
        assertThatThrownBy(() -> EnergyMeasurementEvent.fromMap("1007-0", fields))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("voltage");
        fields.put("voltage", "230.0");
        fields.put("temperature", "-50.0");
        assertThatThrownBy(() -> EnergyMeasurementEvent.fromMap("1007-0", fields))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("temperature");
    }

    @Test
    void shouldUseCollectorSensorIdRules() {
        Map<Object, Object> fields = Map.of(
                "sensorId", "sensor/001", "timestamp", "2026-10-21T12:00:00Z",
                "powerKw", "15.0", "voltage", "230.0");
        assertThatThrownBy(() -> EnergyMeasurementEvent.fromMap("1008-0", fields))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("sensorId");
    }
}
