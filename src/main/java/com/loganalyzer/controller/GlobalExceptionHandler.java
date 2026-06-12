package com.loganalyzer.controller;

import com.loganalyzer.service.DataModelValidator;
import com.loganalyzer.service.IncidentAnalysisService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.util.Map;
import java.util.UUID;

/**
 * Global exception handler for unhandled errors.
 * Generates correlation IDs for troubleshooting.
 */
@ControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * Handle data model validation failures.
     */
    @ExceptionHandler(DataModelValidator.DataValidationException.class)
    @ResponseBody
    public ResponseEntity<Map<String, String>> handleValidationException(DataModelValidator.DataValidationException e) {
        String correlationId = UUID.randomUUID().toString().substring(0, 8);
        log.error("[{}] Data validation failed: {}", correlationId, e.getMessage());

        return ResponseEntity.badRequest().body(Map.of(
                "error", "Validation failed: " + e.getMessage(),
                "correlationId", correlationId
        ));
    }

    /**
     * Handle analysis exceptions (e.g., no exceptions found).
     */
    @ExceptionHandler(IncidentAnalysisService.AnalysisException.class)
    @ResponseBody
    public ResponseEntity<Map<String, String>> handleAnalysisException(IncidentAnalysisService.AnalysisException e) {
        return ResponseEntity.badRequest().body(Map.of(
                "error", e.getMessage()
        ));
    }

    /**
     * Handle file upload size exceeded.
     */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    @ResponseBody
    public ResponseEntity<Map<String, String>> handleMaxUploadSize(MaxUploadSizeExceededException e) {
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE).body(Map.of(
                "error", "File exceeds maximum upload size."
        ));
    }

    /**
     * Catch-all handler for unexpected exceptions.
     * Logs with correlation ID for troubleshooting.
     */
    @ExceptionHandler(Exception.class)
    @ResponseBody
    public ResponseEntity<Map<String, String>> handleUnexpectedException(Exception e) {
        String correlationId = UUID.randomUUID().toString().substring(0, 8);
        log.error("[{}] Unexpected error: {}", correlationId, e.getMessage(), e);

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                "error", "An unexpected error occurred. Correlation ID: " + correlationId,
                "correlationId", correlationId
        ));
    }
}
