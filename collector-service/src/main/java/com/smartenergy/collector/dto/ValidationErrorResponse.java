package com.smartenergy.collector.dto;

import java.time.Instant;
import java.util.Map;

/**
 * Format standardisé pour les erreurs de validation HTTP 400.
 */
public record ValidationErrorResponse(
        int status,
        String error,
        String message,
        Instant timestamp,
        Map<String, String> fieldErrors
) {
    public static ValidationErrorResponse of(String message, Map<String, String> fieldErrors) {
        return new ValidationErrorResponse(
                400,
                "Bad Request",
                message,
                Instant.now(),
                fieldErrors
        );
    }
}
