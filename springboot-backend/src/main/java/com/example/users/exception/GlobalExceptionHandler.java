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
import org.springframework.web.bind.MethodArgumentNotValidException;
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
