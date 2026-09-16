package com.example.users.controller;

import com.example.users.dto.ApiError;
import com.example.users.dto.PageResponse;
import com.example.users.dto.UserRequest;
import com.example.users.dto.UserResponse;
import com.example.users.service.UserService;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.PathVariable;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.Put;
import io.micronaut.http.annotation.QueryValue;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.annotation.ExecuteOn;
import io.micronaut.security.annotation.Secured;
import io.micronaut.security.rules.SecurityRule;
import io.micronaut.validation.Validated;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

import java.net.URI;
import java.util.UUID;

@Controller("/api/v1/users")
@Secured(SecurityRule.IS_AUTHENTICATED)
@ExecuteOn(TaskExecutors.BLOCKING)
@Validated
@Tag(name = "Users")
@SecurityRequirement(name = "bearerAuth")
@ApiResponse(responseCode = "401", description = "Missing, malformed or expired token",
        content = @Content(schema = @Schema(implementation = ApiError.class)))
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @Post
    @Operation(summary = "Create a user", description = "Creates a user record (FR-2).")
    @ApiResponse(responseCode = "201", description = "Created")
    @ApiResponse(responseCode = "400", description = "Validation failed",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "409", description = "Email already in use",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    public HttpResponse<UserResponse> create(@Body @Valid UserRequest request) {
        UserResponse created = userService.create(request);
        return HttpResponse.created(created, URI.create("/api/v1/users/" + created.id()));
    }

    @Get(produces = MediaType.APPLICATION_JSON)
    @Operation(summary = "List users", description = "Paged, sortable, searchable list of users (FR-5).")
    @ApiResponse(responseCode = "200", description = "A page of users")
    @ApiResponse(responseCode = "400", description = "Invalid paging, sort or search parameter",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    public PageResponse<UserResponse> list(
            @Parameter(description = "Zero-based page index") @QueryValue @Nullable Integer page,
            @Parameter(description = "Page size, 1-100") @QueryValue @Nullable Integer size,
            @Parameter(description = "firstName | lastName | email | createdAt | updatedAt") @QueryValue @Nullable String sort,
            @Parameter(description = "asc | desc") @QueryValue @Nullable String direction,
            @Parameter(description = "Free-text match on first name, last name or email") @QueryValue @Nullable String search) {
        return userService.list(page, size, sort, direction, search);
    }

    @Get("/{id}")
    @Operation(summary = "Get a user", description = "Fetches one user by id (FR-4).")
    @ApiResponse(responseCode = "200", description = "The user")
    @ApiResponse(responseCode = "404", description = "No such user",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    public UserResponse get(@PathVariable UUID id) {
        return userService.get(id);
    }

    @Put("/{id}")
    @Operation(summary = "Update a user",
            description = "Full replacement of the editable fields (FR-3). Omitting phone clears it.")
    @ApiResponse(responseCode = "200", description = "The updated user")
    @ApiResponse(responseCode = "400", description = "Validation failed",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "404", description = "No such user",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "409", description = "Email already in use",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    public UserResponse update(@PathVariable UUID id, @Body @Valid UserRequest request) {
        return userService.update(id, request);
    }

    // No DELETE route: v1 does not remove users (BR-4), so Micronaut answers
    // 405 Method Not Allowed on this path, which is the documented behaviour.
}
