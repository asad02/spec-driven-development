package com.example.users.exception;

import com.example.users.config.CorrelationId;
import com.example.users.dto.ApiError;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.annotation.Produces;
import io.micronaut.http.server.exceptions.ExceptionHandler;
import jakarta.inject.Singleton;

import java.util.List;

@Produces
@Singleton
public class DuplicateEmailExceptionHandler implements ExceptionHandler<DuplicateEmailException, HttpResponse<ApiError>> {

    @Override
    public HttpResponse<ApiError> handle(HttpRequest request, DuplicateEmailException exception) {
        // Names the offending field so the UI can attach the message to the
        // email input and leave the dialog open with its data intact (FR-2).
        ApiError body = new ApiError(
                "EMAIL_ALREADY_EXISTS",
                "This email is already in use.",
                CorrelationId.of(request),
                List.of(new ApiError.FieldError("email", "This email is already in use."))
        );
        return HttpResponse.status(HttpStatus.CONFLICT).body(body);
    }
}
