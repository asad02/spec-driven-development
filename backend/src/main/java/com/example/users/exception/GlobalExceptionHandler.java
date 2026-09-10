package com.example.users.exception;

import com.example.users.config.CorrelationId;
import com.example.users.dto.ApiError;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.annotation.Produces;
import io.micronaut.http.server.exceptions.ExceptionHandler;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Last resort. The client gets a generic sentence; the stack trace goes to the
 * log against the same correlation id the caller was handed, so a reported
 * error can be found without guessing.
 */
@Produces
@Singleton
public class GlobalExceptionHandler implements ExceptionHandler<Throwable, HttpResponse<ApiError>> {

    private static final Logger LOG = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @Override
    public HttpResponse<ApiError> handle(HttpRequest request, Throwable exception) {
        String correlationId = CorrelationId.of(request);
        LOG.error("Unhandled error on {} {} (correlationId={})",
                request.getMethod(), request.getPath(), correlationId, exception);

        return HttpResponse.<ApiError>status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiError.of("INTERNAL_ERROR",
                        "Something went wrong. Quote the correlation id when reporting this.",
                        correlationId));
    }
}
