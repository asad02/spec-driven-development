package com.example.users.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/** The single error shape returned by every failure (technical spec §6.6). */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(name = "ApiError", description = "Error envelope shared by every failure response")
public record ApiError(
        @Schema(example = "VALIDATION_FAILED") String code,
        @Schema(example = "Request validation failed.") String message,
        String correlationId,
        List<FieldError> fieldErrors
) {

    public static ApiError of(String code, String message, String correlationId) {
        return new ApiError(code, message, correlationId, null);
    }

    @Schema(name = "FieldError")
    public record FieldError(String field, String message) {
    }
}
