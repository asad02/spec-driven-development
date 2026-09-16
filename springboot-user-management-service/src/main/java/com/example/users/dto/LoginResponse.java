package com.example.users.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * Mirrors Micronaut's BearerAccessRefreshToken exactly — snake_case field names
 * included. Spring Security ships no login endpoint, so this shape is ours to
 * hold still; the SPA and the Postman collection both depend on it verbatim.
 */
@Schema(name = "LoginResponse")
public record LoginResponse(
        String username,
        List<String> roles,
        @JsonProperty("access_token") String accessToken,
        @JsonProperty("token_type") String tokenType,
        @JsonProperty("expires_in") long expiresIn
) {
}
