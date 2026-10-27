package com.smartenergy.collector.exception;

import com.smartenergy.collector.dto.ValidationErrorResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.Map;

/**
 * Gestionnaire global des exceptions HTTP pour intercepter les erreurs de validation
 * et formater une réponse 400 claire et exploitable par les clients IoT.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ValidationErrorResponse> handleValidationExceptions(MethodArgumentNotValidException ex) {
        Map<String, String> fieldErrors = new HashMap<>();
        for (FieldError error : ex.getBindingResult().getFieldErrors()) {
            fieldErrors.put(error.getField(), error.getDefaultMessage());
        }

        ValidationErrorResponse response = ValidationErrorResponse.of(
                "Échec de validation des données de mesure",
                fieldErrors
        );
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ValidationErrorResponse> handleMalformedJson(HttpMessageNotReadableException ex) {
        Map<String, String> fieldErrors = new HashMap<>();
        fieldErrors.put("payload", "Format JSON malformé ou type de données incompatible (vérifiez la syntaxe et le format de date ISO-8601)");

        ValidationErrorResponse response = ValidationErrorResponse.of(
                "Corps de requête illisible ou malformé",
                fieldErrors
        );
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }
}
