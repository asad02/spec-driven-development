package com.example.users.exception;

import com.example.users.config.CorrelationIdFilter;
import com.example.users.dto.ApiError;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.NoHandlerFoundException;

import java.util.Comparator;
import java.util.List;

/**
 * One advice for every failure, replacing the Micronaut service's per-exception
 * handler beans. Every response uses the single ApiError envelope (§6.6).
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger LOG = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(AccessDeniedForFeatureException.class)
    ResponseEntity<ApiError> handleFeatureAccessDenied(AccessDeniedForFeatureException ex) {
        LOG.info("Access denied by feature '{}' [correlationId={}]", ex.getFeature(), correlationId());
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(new ApiError("ACCESS_DENIED", ex.getMessage(), correlationId(),
                        List.of(new ApiError.FieldError("feature", ex.getFeature()))));
    }




    @ExceptionHandler(UserNotFoundException.class)
    ResponseEntity<ApiError> handleNotFound(UserNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiError.of("USER_NOT_FOUND", ex.getMessage(), correlationId()));
    }

    @ExceptionHandler(DuplicateEmailException.class)
    ResponseEntity<ApiError> handleDuplicate(DuplicateEmailException ex) {
        // Names the offending field so the UI can attach the message to the input.
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ApiError("EMAIL_ALREADY_EXISTS", ex.getMessage(), correlationId(),
                        List.of(new ApiError.FieldError("email", "This email is already in use."))));
    }

    @ExceptionHandler(InvalidParameterException.class)
    ResponseEntity<ApiError> handleInvalidParameter(InvalidParameterException ex) {
        return ResponseEntity.badRequest()
                .body(new ApiError("INVALID_PARAMETER", ex.getMessage(), correlationId(),
                        List.of(new ApiError.FieldError(ex.getParameter(), ex.getMessage()))));
    }

    /** Aggregates every violation, never just the first (acceptance §10.5). */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException ex) {
        List<ApiError.FieldError> fieldErrors = ex.getBindingResult().getFieldErrors().stream()
                .map(f -> new ApiError.FieldError(f.getField(), f.getDefaultMessage()))
                .sorted(Comparator.comparing(ApiError.FieldError::field))
                .toList();
        return ResponseEntity.badRequest()
                .body(new ApiError("VALIDATION_FAILED", "Request validation failed.", correlationId(), fieldErrors));
    }

    @ExceptionHandler({BadCredentialsException.class, DisabledException.class, AuthenticationException.class})
    ResponseEntity<ApiError> handleAuth(AuthenticationException ex) {
        // Deliberately identical for unknown user, wrong password and disabled account.
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ApiError.of("INVALID_CREDENTIALS", "Invalid username or password.", correlationId()));
    }

    @ExceptionHandler(NoHandlerFoundException.class)
    ResponseEntity<ApiError> handleNoHandler(NoHandlerFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiError.of("NOT_FOUND", "No such resource.", correlationId()));
    }

    /*
     * Spring's own exceptions must be mapped explicitly. The catch-all below would
     * otherwise turn every one of them into a 500 — so an unsupported method read as
     * a server fault rather than 405, breaking parity with the Micronaut service and
     * the contract in functional spec §7.
     */

    /**
     * A path or query value that cannot be converted — most often a malformed UUID.
     * The caller sent something unusable; that is a 400, and the Micronaut service
     * answers the same way.
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ResponseEntity<ApiError> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        return ResponseEntity.badRequest()
                .body(new ApiError("INVALID_PARAMETER",
                        "'" + ex.getName() + "' is not a valid value.", correlationId(),
                        List.of(new ApiError.FieldError(ex.getName(), "is not a valid value"))));
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    ResponseEntity<ApiError> handleMissingParameter(MissingServletRequestParameterException ex) {
        return ResponseEntity.badRequest()
                .body(new ApiError("INVALID_PARAMETER",
                        "'" + ex.getParameterName() + "' is required.", correlationId(),
                        List.of(new ApiError.FieldError(ex.getParameterName(), "is required"))));
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    ResponseEntity<ApiError> handleMethodNotAllowed(HttpRequestMethodNotSupportedException ex) {
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED)
                .body(ApiError.of("METHOD_NOT_ALLOWED",
                        ex.getMethod() + " is not supported on this resource.", correlationId()));
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    ResponseEntity<ApiError> handleUnsupportedMediaType(HttpMediaTypeNotSupportedException ex) {
        return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
                .body(ApiError.of("UNSUPPORTED_MEDIA_TYPE",
                        "This endpoint accepts application/json.", correlationId()));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ApiError> handleUnreadableBody(HttpMessageNotReadableException ex) {
        // A malformed body is the client's mistake, not the server's.
        return ResponseEntity.badRequest()
                .body(ApiError.of("MALFORMED_REQUEST",
                        "The request body could not be read as JSON.", correlationId()));
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
