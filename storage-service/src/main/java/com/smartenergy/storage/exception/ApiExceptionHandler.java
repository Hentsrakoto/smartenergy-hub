package com.smartenergy.storage.exception;

import com.smartenergy.storage.mongo.exception.DuplicateSensorException;
import com.smartenergy.storage.mongo.exception.NoMeasurementsException;
import com.smartenergy.storage.mongo.exception.SensorNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Gestionnaire global d'erreurs HTTP du Storage Service.
 *
 * <p>Il produit des réponses JSON homogènes, sans stack trace, et distingue
 * les erreurs de validation (400), les ressources absentes (404), les conflits
 * d'identifiant (409) et les erreurs internes (500).</p>
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleValidation(
            MethodArgumentNotValidException ex,
            HttpServletRequest request) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        for (FieldError error : ex.getBindingResult().getFieldErrors()) {
            String message = error.getDefaultMessage() == null ? "valeur invalide" : error.getDefaultMessage();
            fieldErrors.merge(error.getField(), message, (first, second) -> first + "; " + second);
        }
        return build(HttpStatus.BAD_REQUEST,
                "Échec de validation des données du capteur", request, fieldErrors);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiErrorResponse> handleMalformedJson(
            HttpMessageNotReadableException ex,
            HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST,
                "Corps JSON illisible ou mal formé. Vérifiez la syntaxe et les types de données.",
                request, null);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiErrorResponse> handleTypeMismatch(
            MethodArgumentTypeMismatchException ex,
            HttpServletRequest request) {
        String expected = "date".equals(ex.getName()) ? " Format attendu : YYYY-MM-DD." : "";
        return build(HttpStatus.BAD_REQUEST,
                "Paramètre '" + ex.getName() + "' invalide : " + ex.getValue() + "." + expected,
                request, null);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiErrorResponse> handleMissingParameter(
            MissingServletRequestParameterException ex,
            HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST,
                "Paramètre obligatoire manquant : " + ex.getParameterName(), request, null);
    }

    @ExceptionHandler(SensorNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleSensorNotFound(
            SensorNotFoundException ex,
            HttpServletRequest request) {
        return build(HttpStatus.NOT_FOUND, ex.getMessage(), request, null);
    }

    @ExceptionHandler(NoMeasurementsException.class)
    public ResponseEntity<ApiErrorResponse> handleNoMeasurements(
            NoMeasurementsException ex,
            HttpServletRequest request) {
        return build(HttpStatus.NOT_FOUND, ex.getMessage(), request, null);
    }

    @ExceptionHandler(DuplicateSensorException.class)
    public ResponseEntity<ApiErrorResponse> handleDuplicateSensor(
            DuplicateSensorException ex,
            HttpServletRequest request) {
        return build(HttpStatus.CONFLICT, ex.getMessage(), request, null);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiErrorResponse> handleIllegalArgument(
            IllegalArgumentException ex,
            HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST, ex.getMessage(), request, null);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleNoResource(
            NoResourceFoundException ex,
            HttpServletRequest request) {
        return build(HttpStatus.NOT_FOUND, "Ressource introuvable", request, null);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiErrorResponse> handleMethodNotSupported(
            HttpRequestMethodNotSupportedException ex,
            HttpServletRequest request) {
        return build(HttpStatus.METHOD_NOT_ALLOWED,
                "Méthode HTTP non supportée pour cette ressource", request, null);
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ApiErrorResponse> handleMediaTypeNotSupported(
            HttpMediaTypeNotSupportedException ex,
            HttpServletRequest request) {
        return build(HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                "Type de contenu non supporté. Utilisez application/json.", request, null);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleUnexpected(
            Exception ex,
            HttpServletRequest request) {
        log.error("Erreur interne non gérée sur {} {}", request.getMethod(), request.getRequestURI(), ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR,
                "Erreur interne du serveur. Consultez les logs du Storage Service.", request, null);
    }

    private ResponseEntity<ApiErrorResponse> build(HttpStatus status,
                                                   String message,
                                                   HttpServletRequest request,
                                                   Map<String, String> fieldErrors) {
        return ResponseEntity.status(status)
                .body(ApiErrorResponse.of(status, message, request.getRequestURI(), fieldErrors));
    }
}
