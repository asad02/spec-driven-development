package com.example.users.exception;

import com.example.users.config.CorrelationId;
import com.example.users.dto.ApiError;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.Produces;
import io.micronaut.http.server.exceptions.ExceptionHandler;
import jakarta.inject.Singleton;

@Produces
@Singleton
public class UserNotFoundExceptionHandler implements ExceptionHandler<UserNotFoundException, HttpResponse<ApiError>> {

    @Override
    public HttpResponse<ApiError> handle(HttpRequest request, UserNotFoundException exception) {
        return HttpResponse.notFound(
                ApiError.of("USER_NOT_FOUND", exception.getMessage(), CorrelationId.of(request)));
    }
}
