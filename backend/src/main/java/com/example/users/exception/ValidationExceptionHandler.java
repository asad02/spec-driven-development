package com.example.users.exception;

import com.example.users.config.CorrelationId;
import com.example.users.dto.ApiError;
import io.micronaut.context.annotation.Replaces;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.Produces;
import io.micronaut.http.server.exceptions.ExceptionHandler;
import io.micronaut.validation.exceptions.ConstraintExceptionHandler;
import jakarta.inject.Singleton;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Path;

import java.util.Comparator;
import java.util.List;

/**
 * Replaces Micronaut's default constraint handler so validation failures use
 * the one error envelope, and — per acceptance criterion 05 — report every
 * offending field at once rather than only the first.
 */
@Produces
@Singleton
@Replaces(ConstraintExceptionHandler.class)
public class ValidationExceptionHandler implements ExceptionHandler<ConstraintViolationException, HttpResponse<?>> {

    @Override
    public HttpResponse<?> handle(HttpRequest request, ConstraintViolationException exception) {
        List<ApiError.FieldError> fieldErrors = exception.getConstraintViolations().stream()
                .map(v -> new ApiError.FieldError(fieldName(v), v.getMessage()))
                .sorted(Comparator.comparing(ApiError.FieldError::field))
                .toList();

        ApiError body = new ApiError(
                "VALIDATION_FAILED",
                "Request validation failed.",
                CorrelationId.of(request),
                fieldErrors
        );
        return HttpResponse.badRequest(body);
    }

    /**
     * A violation path reads {@code create.request.email}; the client only cares
     * about the last node, which is the field it can highlight.
     */
    private static String fieldName(ConstraintViolation<?> violation) {
        String last = null;
        for (Path.Node node : violation.getPropertyPath()) {
            if (node.getName() != null) {
                last = node.getName();
            }
        }
        return last == null ? "request" : last;
    }
}
