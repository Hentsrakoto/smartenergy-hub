package com.smartenergy.collector.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;

import java.time.Instant;

/**
 * Contrat de données d'une mesure énergétique reçue d'un capteur IoT.
 *
 * Utilise Jakarta Validation pour vérifier la validité structurelle et physique des mesures
 * avant toute acceptation dans la plateforme SmartEnergy Hub.
 */
public record EnergyMeasurementRequest(

        @NotBlank(message = "L'identifiant du capteur (sensorId) est obligatoire")
        @Pattern(
                regexp = "^[a-zA-Z0-9_-]{3,50}$",
                message = "L'identifiant du capteur doit comporter entre 3 et 50 caractères alphanumériques, tirets ou underscores"
        )
        String sensorId,

        @NotNull(message = "L'horodatage (timestamp) est obligatoire")
        @JsonFormat(shape = JsonFormat.Shape.STRING, timezone = "UTC")
        Instant timestamp,

        @NotNull(message = "La puissance active (powerKw) est obligatoire")
        @PositiveOrZero(message = "La puissance active doit être positive ou nulle")
        @DecimalMax(value = "5000.0", message = "La puissance active ne peut excéder 5000 kW (limite physique industrielle)")
        Double powerKw,

        @NotNull(message = "La tension (voltage) est obligatoire")
        @DecimalMin(value = "150.0", message = "La tension ne peut être inférieure à 150.0 V (seuil bas réseau électrique)")
        @DecimalMax(value = "300.0", message = "La tension ne peut excéder 300.0 V (seuil haut réseau électrique)")
        Double voltage,

        @DecimalMin(value = "-40.0", message = "La température ne peut être inférieure à -40.0 °C")
        @DecimalMax(value = "125.0", message = "La température ne peut excéder 125.0 °C")
        Double temperature
) {
}
