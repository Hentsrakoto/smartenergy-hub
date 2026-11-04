package com.smartenergy.analytics.exception;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.springframework.http.HttpStatus;

import java.time.Instant;
import java.util.Map;

/**
 * Structure homogène des erreurs HTTP renvoyées par le Analytics Service.
 * Les détails internes (stack trace, secrets) ne sont jamais exposés.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiErrorResponse(
        int status,
        String error,
        String message,
        Instant timestamp,
        String path,
        Map<String, String> fieldErrors,
        String code
) {

    public static ApiErrorResponse sensorNotFound(String message, String path) {
        return new ApiErrorResponse(404, "Not Found", message, Instant.now(), path, null, "SENSOR_NOT_FOUND");
    }

    public static ApiErrorResponse of(HttpStatus status,
                                      String message,
                                      String path,
                                      Map<String, String> fieldErrors) {
        return new ApiErrorResponse(
                status.value(),
                status.getReasonPhrase(),
                message,
                Instant.now(),
                path,
                fieldErrors,
                null
        );
    }
}
