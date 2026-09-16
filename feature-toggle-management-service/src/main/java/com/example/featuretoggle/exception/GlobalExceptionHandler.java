package com.example.featuretoggle.exception;

import com.example.featuretoggle.api.ApiError;
import com.example.featuretoggle.config.CorrelationIdFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.Comparator;
import java.util.List;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger LOG = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(AccessDeniedForFeatureException.class)
    ResponseEntity<ApiError> handleAccessDenied(AccessDeniedForFeatureException ex) {
        LOG.info("Access denied by feature '{}' [correlationId={}]", ex.getFeature(), correlationId());
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(new ApiError("ACCESS_DENIED", ex.getMessage(), correlationId(),
                        List.of(new ApiError.FieldError("feature", ex.getFeature()))));
    }

    @ExceptionHandler(FeatureNotFoundException.class)
    ResponseEntity<ApiError> handleNotFound(FeatureNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiError.of("FEATURE_NOT_FOUND", ex.getMessage(), correlationId()));
    }

    @ExceptionHandler(FeatureAlreadyExistsException.class)
    ResponseEntity<ApiError> handleExists(FeatureAlreadyExistsException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ApiError("FEATURE_ALREADY_EXISTS", ex.getMessage(), correlationId(),
                        List.of(new ApiError.FieldError("uid", "This feature id is already in use."))));
    }

    @ExceptionHandler(FeatureProtectedException.class)
    ResponseEntity<ApiError> handleProtected(FeatureProtectedException ex) {
        LOG.warn("Refused a change that would break feature '{}': {}", ex.getFeature(), ex.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ApiError("FEATURE_PROTECTED", ex.getMessage(), correlationId(),
                        List.of(new ApiError.FieldError("feature", ex.getFeature()))));
    }

    @ExceptionHandler(InvalidParameterException.class)
    ResponseEntity<ApiError> handleInvalidParameter(InvalidParameterException ex) {
        return ResponseEntity.badRequest()
                .body(new ApiError("INVALID_PARAMETER", ex.getMessage(), correlationId(),
                        List.of(new ApiError.FieldError(ex.getParameter(), ex.getMessage()))));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException ex) {
        List<ApiError.FieldError> fieldErrors = ex.getBindingResult().getFieldErrors().stream()
                .map(f -> new ApiError.FieldError(f.getField(), f.getDefaultMessage()))
                .sorted(Comparator.comparing(ApiError.FieldError::field))
                .toList();
        return ResponseEntity.badRequest()
                .body(new ApiError("VALIDATION_FAILED", "Request validation failed.", correlationId(), fieldErrors));
    }

    /*
     * Spring's own exceptions are mapped explicitly. The catch-all below would
     * otherwise turn each into a 500, so an unsupported method would read as a
     * server fault rather than 405.
     */

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    ResponseEntity<ApiError> handleMethodNotAllowed(HttpRequestMethodNotSupportedException ex) {
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED)
                .body(ApiError.of("METHOD_NOT_ALLOWED",
                        ex.getMethod() + " is not supported on this resource.", correlationId()));
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    ResponseEntity<ApiError> handleUnsupportedMediaType(HttpMediaTypeNotSupportedException ex) {
        return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
                .body(ApiError.of("UNSUPPORTED_MEDIA_TYPE", "This endpoint accepts application/json.", correlationId()));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ApiError> handleUnreadableBody(HttpMessageNotReadableException ex) {
        return ResponseEntity.badRequest()
                .body(ApiError.of("MALFORMED_REQUEST", "The request body could not be read as JSON.", correlationId()));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    ResponseEntity<ApiError> handleNoResource(NoResourceFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiError.of("NOT_FOUND", "No such resource.", correlationId()));
    }

    @ExceptionHandler(Throwable.class)
    ResponseEntity<ApiError> handleEverythingElse(Throwable ex) {
        String correlationId = correlationId();
        LOG.error("Unhandled failure [correlationId={}]", correlationId, ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiError.of("INTERNAL_ERROR",
                        "Something went wrong. Quote the correlation id when reporting this.", correlationId));
    }

    private static String correlationId() {
        return MDC.get(CorrelationIdFilter.MDC_KEY);
    }
}
