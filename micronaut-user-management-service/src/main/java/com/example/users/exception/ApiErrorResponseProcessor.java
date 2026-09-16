package com.example.users.exception;

import com.example.users.config.CorrelationId;
import com.example.users.dto.ApiError;
import io.micronaut.context.annotation.Replaces;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.http.server.exceptions.response.ErrorContext;
import io.micronaut.http.server.exceptions.response.ErrorResponseProcessor;
import io.micronaut.http.server.exceptions.response.HateoasErrorResponseProcessor;
import jakarta.inject.Singleton;

import java.util.List;

/**
 * Renders Micronaut's own framework errors — 401, 405, and anything else the router
 * produces before a controller runs — in the same {@link ApiError} envelope the
 * application uses.
 *
 * <p>Without this, those responses come back in Micronaut's HAL-ish default shape
 * (`{"message":…,"_links":…,"_embedded":…}`), which is not the envelope this
 * service's own specification documents and does not match the Spring Boot
 * implementation. Replacing the processor closes that drift in one place rather
 * than by adding a handler per framework exception.
 */
@Singleton
@Replaces(HateoasErrorResponseProcessor.class)
public class ApiErrorResponseProcessor implements ErrorResponseProcessor<ApiError> {

    @Override
    public MutableHttpResponse<ApiError> processResponse(ErrorContext context,
                                                         MutableHttpResponse<?> response) {
        HttpStatus status = response.status();
        String correlationId = CorrelationId.of(context.getRequest());

        List<ApiError.FieldError> fieldErrors = context.getErrors().stream()
                .filter(e -> e.getPath().isPresent())
                .map(e -> new ApiError.FieldError(e.getPath().get(), e.getMessage()))
                .toList();

        ApiError body = new ApiError(
                codeFor(status),
                messageFor(context, status),
                correlationId,
                fieldErrors.isEmpty() ? null : fieldErrors);

        return response.body(body);
    }

    private static String codeFor(HttpStatus status) {
        return switch (status) {
            case UNAUTHORIZED -> "UNAUTHENTICATED";
            case FORBIDDEN -> "ACCESS_DENIED";
            case METHOD_NOT_ALLOWED -> "METHOD_NOT_ALLOWED";
            case NOT_FOUND -> "NOT_FOUND";
            case UNSUPPORTED_MEDIA_TYPE -> "UNSUPPORTED_MEDIA_TYPE";
            case BAD_REQUEST -> "INVALID_PARAMETER";
            default -> status.getCode() >= 500 ? "INTERNAL_ERROR" : "REQUEST_REJECTED";
        };
    }

    private static String messageFor(ErrorContext context, HttpStatus status) {
        // Never surface a framework exception message on a 5xx: it can carry
        // internals, and the documented contract is a generic string.
        if (status.getCode() >= 500) {
            return "Something went wrong. Quote the correlation id when reporting this.";
        }
        return context.getErrors().stream().findFirst()
                .map(io.micronaut.http.server.exceptions.response.Error::getMessage)
                .orElse(status.getReason());
    }
}
