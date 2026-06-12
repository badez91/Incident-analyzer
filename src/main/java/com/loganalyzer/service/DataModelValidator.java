package com.loganalyzer.service;

import com.loganalyzer.model.CanonicalEvent;
import com.loganalyzer.model.IncidentAnalysis;
import com.loganalyzer.model.ScoredMatch;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Validates data models before persistence to the Knowledge Store.
 * Ensures invalid records are never persisted to PostgreSQL.
 */
@Component
public class DataModelValidator {

    private final Validator validator;

    public DataModelValidator() {
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        this.validator = factory.getValidator();
    }

    /**
     * Validate a CanonicalEvent.
     * @throws DataValidationException if validation fails
     */
    public void validate(CanonicalEvent event) {
        Set<ConstraintViolation<CanonicalEvent>> violations = validator.validate(event);
        if (!violations.isEmpty()) {
            String errors = violations.stream()
                    .map(v -> v.getPropertyPath() + ": " + v.getMessage())
                    .collect(Collectors.joining("; "));
            throw new DataValidationException("CanonicalEvent validation failed: " + errors);
        }
    }

    /**
     * Validate an IncidentAnalysis for consistency.
     * @throws DataValidationException if validation fails
     */
    public void validate(IncidentAnalysis analysis) {
        // TimeRange: start <= end
        if (analysis.getTimeRangeStart() != null && analysis.getTimeRangeEnd() != null) {
            if (analysis.getTimeRangeStart().isAfter(analysis.getTimeRangeEnd())) {
                throw new DataValidationException(
                        "timeRange: start must be before or equal to end");
            }
        }

        // ExceptionTypes must equal keySet of exceptionCounts
        if (analysis.getExceptionCounts() != null && analysis.getExceptionTypes() != null) {
            Set<String> expectedTypes = analysis.getExceptionCounts().keySet();
            if (!analysis.getExceptionTypes().equals(expectedTypes)) {
                throw new DataValidationException(
                        "exceptionTypes must equal the key set of exceptionCounts. " +
                        "Expected: " + expectedTypes + ", Got: " + analysis.getExceptionTypes());
            }
        }

        // ErrorDistribution percentages must sum to ~100 (±0.1 tolerance)
        if (analysis.getErrorDistribution() != null && !analysis.getErrorDistribution().isEmpty()) {
            double sum = analysis.getErrorDistribution().values().stream()
                    .mapToDouble(Double::doubleValue)
                    .sum();
            if (sum < 99.9 || sum > 100.1) {
                throw new DataValidationException(
                        "errorDistribution: percentages must sum to ~100.0 (±0.1). Got: " + sum);
            }
        }

        // Status must be valid
        String status = analysis.getStatus();
        if (status != null && !status.equals("COMPLETE") && !status.equals("INCOMPLETE") && !status.equals("FAILED")) {
            throw new DataValidationException(
                    "status: must be one of COMPLETE, INCOMPLETE, FAILED. Got: " + status);
        }
    }

    /**
     * Validate a ScoredMatch.
     * @throws DataValidationException if validation fails
     */
    public void validate(ScoredMatch match) {
        // Score must be in [0.0, 1.0]
        if (match.getSimilarityScore() < 0.0 || match.getSimilarityScore() > 1.0) {
            throw new DataValidationException(
                    "similarityScore: must be between 0.0 and 1.0. Got: " + match.getSimilarityScore());
        }

        // MatchReasons must not be empty
        Map<String, String> reasons = match.getMatchReasons();
        if (reasons == null || reasons.isEmpty()) {
            throw new DataValidationException(
                    "matchReasons: must contain at least one entry");
        }
    }

    /**
     * Exception thrown when data model validation fails.
     */
    public static class DataValidationException extends RuntimeException {
        public DataValidationException(String message) {
            super(message);
        }
    }
}
