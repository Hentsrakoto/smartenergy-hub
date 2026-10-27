package com.smartenergy.storage.mongo.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Corps de requête pour créer ou modifier un capteur.
 *
 * <p>{@code sensorId} est obligatoire à la création. Lors d'un {@code PUT}, l'identifiant
 * du chemin est autoritaire ; s'il est présent dans le corps, il doit être identique.</p>
 */
public record SensorRequest(
        @Pattern(regexp = "^[a-zA-Z0-9_-]{3,50}$",
                message = "sensorId doit comporter entre 3 et 50 caractères alphanumériques, tirets ou underscores")
        String sensorId,

        @NotBlank(message = "name est obligatoire")
        @Size(max = 120, message = "name ne doit pas dépasser 120 caractères")
        String name,

        @Size(max = 120, message = "building ne doit pas dépasser 120 caractères")
        String building,

        @Min(value = -10, message = "floor doit être supérieur ou égal à -10")
        Integer floor,

        @NotNull(message = "maxPowerKw est obligatoire")
        @PositiveOrZero(message = "maxPowerKw doit être positif ou nul")
        Double maxPowerKw,

        @NotNull(message = "active est obligatoire")
        Boolean active,

        @Size(max = 20, message = "tags ne doit pas dépasser 20 éléments")
        List<@NotBlank(message = "un tag ne doit pas être vide") String> tags,

        @PositiveOrZero(message = "minVoltage doit être positif ou nul")
        Double minVoltage,

        @PositiveOrZero(message = "maxVoltage doit être positif ou nul")
        Double maxVoltage,

        @DecimalMin(value = "-100.0", message = "maxTemperature doit être supérieur ou égal à -100")
        @DecimalMax(value = "200.0", message = "maxTemperature doit être inférieur ou égal à 200")
        Double maxTemperature,

        @Min(value = 1, message = "samplingIntervalSeconds doit être supérieur ou égal à 1")
        @Max(value = 86400, message = "samplingIntervalSeconds doit être inférieur ou égal à 86400")
        Integer samplingIntervalSeconds
) {
}
