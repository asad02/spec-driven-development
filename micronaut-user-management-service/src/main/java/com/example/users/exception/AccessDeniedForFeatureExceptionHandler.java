package com.example.users.exception;

import com.example.users.config.CorrelationId;
import com.example.users.dto.ApiError;
import io.micronaut.context.annotation.Requires;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.server.exceptions.ExceptionHandler;
import io.micronaut.http.server.exceptions.response.ErrorResponseProcessor;
import jakarta.inject.Singleton;

import java.util.List;

@Singleton
@Requires(classes = {AccessDeniedForFeatureException.class, ExceptionHandler.class})
public class AccessDeniedForFeatureExceptionHandler
        implements ExceptionHandler<AccessDeniedForFeatureException, HttpResponse<ApiError>> {

    @Override
    public HttpResponse<ApiError> handle(HttpRequest request, AccessDeniedForFeatureException exception) {
        ApiError body = new ApiError(
                "ACCESS_DENIED",
                exception.getMessage(),
                CorrelationId.of(request),
                List.of(new ApiError.FieldError("feature", exception.getFeature())));
        return HttpResponse.<ApiError>status(HttpStatus.FORBIDDEN).body(body);
    }
}
