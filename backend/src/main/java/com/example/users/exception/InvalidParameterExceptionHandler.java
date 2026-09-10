package com.example.users.exception;

import com.example.users.config.CorrelationId;
import com.example.users.dto.ApiError;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.Produces;
import io.micronaut.http.server.exceptions.ExceptionHandler;
import jakarta.inject.Singleton;

import java.util.List;

@Produces
@Singleton
public class InvalidParameterExceptionHandler implements ExceptionHandler<InvalidParameterException, HttpResponse<ApiError>> {

    @Override
    public HttpResponse<ApiError> handle(HttpRequest request, InvalidParameterException exception) {
        ApiError body = new ApiError(
                "INVALID_PARAMETER",
                exception.getMessage(),
                CorrelationId.of(request),
                List.of(new ApiError.FieldError(exception.getParameter(), exception.getMessage()))
        );
        return HttpResponse.badRequest(body);
    }
}
