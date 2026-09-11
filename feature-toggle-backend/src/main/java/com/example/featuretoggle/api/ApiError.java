package com.example.featuretoggle.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/** The single error shape returned by every failure — same envelope as the product services. */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(name = "ApiError")
public record ApiError(
        String code,
        String message,
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
