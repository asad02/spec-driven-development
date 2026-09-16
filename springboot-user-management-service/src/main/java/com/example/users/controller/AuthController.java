package com.example.users.controller;

import com.example.users.dto.ApiError;
import com.example.users.dto.LoginRequest;
import com.example.users.dto.LoginResponse;
import com.example.users.security.TokenService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Spring Security ships no login endpoint, so this is written by hand — which is
 * why, unlike the Micronaut service, login appears in the generated OpenAPI
 * document (technical-spec-springboot §6.2).
 */
@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Authentication")
@SecurityRequirements
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final TokenService tokenService;

    public AuthController(AuthenticationManager authenticationManager, TokenService tokenService) {
        this.authenticationManager = authenticationManager;
        this.tokenService = tokenService;
    }

    @PostMapping("/login")
    @Operation(summary = "Log in", description = "Exchanges operator credentials for a bearer token (FR-6).")
    @ApiResponse(responseCode = "200", description = "A signed token")
    @ApiResponse(responseCode = "401", description = "Invalid credentials — identical for unknown user, bad password and disabled account",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.username(), request.password()));

        return new LoginResponse(
                authentication.getName(),
                tokenService.roles(authentication),
                tokenService.generate(authentication),
                "Bearer",
                tokenService.getExpirySeconds());
    }
}
