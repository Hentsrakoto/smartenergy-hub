package com.smartenergy.collector.dto;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class EnergyMeasurementRequestValidationTest {

    private static Validator validator;

    @BeforeAll
    static void setUpValidator() {
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @Test
    @DisplayName("Mesure valide complète doit être acceptée sans violation")
    void testValidMeasurementWithAllFields() {
        EnergyMeasurementRequest request = new EnergyMeasurementRequest(
                "sensor-001",
                Instant.parse("2026-10-20T14:30:00Z"),
                12.5,
                230.5,
                22.0
        );

        Set<ConstraintViolation<EnergyMeasurementRequest>> violations = validator.validate(request);
        assertThat(violations).isEmpty();
    }

    @Test
    @DisplayName("Mesure valide sans température doit être acceptée (température optionnelle)")
    void testValidMeasurementWithoutTemperature() {
        EnergyMeasurementRequest request = new EnergyMeasurementRequest(
                "sensor_building_A",
                Instant.now(),
                0.0, // Puissance nulle autorisée
                228.0,
                null
        );

        Set<ConstraintViolation<EnergyMeasurementRequest>> violations = validator.validate(request);
        assertThat(violations).isEmpty();
    }

    @Test
    @DisplayName("sensorId vide ou null doit être rejeté")
    void testInvalidSensorId() {
        EnergyMeasurementRequest nullSensor = new EnergyMeasurementRequest(
                null,
                Instant.now(),
                5.0,
                230.0,
                20.0
        );
        Set<ConstraintViolation<EnergyMeasurementRequest>> violationsNull = validator.validate(nullSensor);
        assertThat(violationsNull).isNotEmpty();
        assertThat(violationsNull).anyMatch(v -> v.getPropertyPath().toString().equals("sensorId"));

        EnergyMeasurementRequest blankSensor = new EnergyMeasurementRequest(
                "   ",
                Instant.now(),
                5.0,
                230.0,
                20.0
        );
        Set<ConstraintViolation<EnergyMeasurementRequest>> violationsBlank = validator.validate(blankSensor);
        assertThat(violationsBlank).isNotEmpty();
    }

    @Test
    @DisplayName("sensorId avec caractères spéciaux non autorisés doit être rejeté")
    void testSensorIdInvalidPattern() {
        EnergyMeasurementRequest request = new EnergyMeasurementRequest(
                "sensor@invalid#name!",
                Instant.now(),
                5.0,
                230.0,
                20.0
        );
        Set<ConstraintViolation<EnergyMeasurementRequest>> violations = validator.validate(request);
        assertThat(violations).isNotEmpty();
        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("sensorId"));
    }

    @Test
    @DisplayName("timestamp null doit être rejeté")
    void testNullTimestamp() {
        EnergyMeasurementRequest request = new EnergyMeasurementRequest(
                "sensor-001",
                null,
                5.0,
                230.0,
                20.0
        );
        Set<ConstraintViolation<EnergyMeasurementRequest>> violations = validator.validate(request);
        assertThat(violations).hasSize(1);
        assertThat(violations.iterator().next().getPropertyPath().toString()).isEqualTo("timestamp");
    }

    @Test
    @DisplayName("powerKw négatif doit être rejeté")
    void testNegativePower() {
        EnergyMeasurementRequest request = new EnergyMeasurementRequest(
                "sensor-001",
                Instant.now(),
                -0.5,
                230.0,
                20.0
        );
        Set<ConstraintViolation<EnergyMeasurementRequest>> violations = validator.validate(request);
        assertThat(violations).isNotEmpty();
        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("powerKw"));
    }

    @Test
    @DisplayName("powerKw dépassant 5000 kW doit être rejeté")
    void testExcessivePower() {
        EnergyMeasurementRequest request = new EnergyMeasurementRequest(
                "sensor-001",
                Instant.now(),
                6000.0,
                230.0,
                20.0
        );
        Set<ConstraintViolation<EnergyMeasurementRequest>> violations = validator.validate(request);
        assertThat(violations).isNotEmpty();
        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("powerKw"));
    }

    @Test
    @DisplayName("Tension inférieure à 150 V ou supérieure à 300 V doit être rejetée")
    void testOutOfRangeVoltage() {
        EnergyMeasurementRequest lowVoltage = new EnergyMeasurementRequest(
                "sensor-001",
                Instant.now(),
                10.0,
                120.0,
                20.0
        );
        Set<ConstraintViolation<EnergyMeasurementRequest>> violationsLow = validator.validate(lowVoltage);
        assertThat(violationsLow).anyMatch(v -> v.getPropertyPath().toString().equals("voltage"));

        EnergyMeasurementRequest highVoltage = new EnergyMeasurementRequest(
                "sensor-001",
                Instant.now(),
                10.0,
                320.0,
                20.0
        );
        Set<ConstraintViolation<EnergyMeasurementRequest>> violationsHigh = validator.validate(highVoltage);
        assertThat(violationsHigh).anyMatch(v -> v.getPropertyPath().toString().equals("voltage"));
    }

    @Test
    @DisplayName("Température hors de la plage physique [-40°C, 125°C] doit être rejetée")
    void testOutOfRangeTemperature() {
        EnergyMeasurementRequest extremeCold = new EnergyMeasurementRequest(
                "sensor-001",
                Instant.now(),
                10.0,
                230.0,
                -50.0
        );
        Set<ConstraintViolation<EnergyMeasurementRequest>> violationsCold = validator.validate(extremeCold);
        assertThat(violationsCold).anyMatch(v -> v.getPropertyPath().toString().equals("temperature"));

        EnergyMeasurementRequest extremeHot = new EnergyMeasurementRequest(
                "sensor-001",
                Instant.now(),
                10.0,
                230.0,
                150.0
        );
        Set<ConstraintViolation<EnergyMeasurementRequest>> violationsHot = validator.validate(extremeHot);
        assertThat(violationsHot).anyMatch(v -> v.getPropertyPath().toString().equals("temperature"));
    }
}
