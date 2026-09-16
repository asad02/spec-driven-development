package com.example.users.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * The full editable state of a user. Used for both create (FR-2) and update
 * (FR-3) — update is a complete replacement, so omitting phone clears it.
 */
@Schema(name = "UserRequest", description = "Editable fields of a user record")
public record UserRequest(

        @NotBlank
        @Size(min = 1, max = 50)
        @Schema(example = "Jane", requiredMode = Schema.RequiredMode.REQUIRED)
        String firstName,

        @NotBlank
        @Size(min = 1, max = 50)
        @Schema(example = "Doe", requiredMode = Schema.RequiredMode.REQUIRED)
        String lastName,

        @NotBlank
        @Email
        @Size(max = 254)
        @Schema(example = "jane@example.com", requiredMode = Schema.RequiredMode.REQUIRED)
        String email,

        @Size(min = 7, max = 20)
        @Pattern(regexp = "^\\+?[0-9 ()\\-]{7,20}$", message = "must be 7-20 digits, optionally with +, spaces, hyphens or parentheses")
        @Schema(example = "+1 555 0100", nullable = true)
        String phone
) {
}
