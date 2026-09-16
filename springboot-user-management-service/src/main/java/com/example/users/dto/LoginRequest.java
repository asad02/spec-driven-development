package com.example.users.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(name = "LoginRequest")
public record LoginRequest(
        @NotBlank @Schema(example = "admin", requiredMode = Schema.RequiredMode.REQUIRED) String username,
        @NotBlank @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String password
) {
}
