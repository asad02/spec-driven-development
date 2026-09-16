package com.example.users.controller;

import com.example.users.dto.ApiError;
import com.example.users.dto.PageResponse;
import com.example.users.dto.UserRequest;
import com.example.users.dto.UserResponse;
import com.example.users.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/users")
@Tag(name = "Users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @PostMapping
    @Operation(summary = "Create user", description = "Creates a user record (FR-2).")
    @ApiResponse(responseCode = "201", description = "Created")
    @ApiResponse(responseCode = "400", description = "Validation failed",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "409", description = "Email already in use",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    public ResponseEntity<UserResponse> create(@Valid @RequestBody UserRequest request) {
        UserResponse created = userService.create(request);
        return ResponseEntity.created(URI.create("/api/v1/users/" + created.id())).body(created);
    }

    @GetMapping
    @Operation(summary = "List users", description = "Paged, sortable, searchable list of users (FR-5).")
    @ApiResponse(responseCode = "200", description = "A page of users")
    @ApiResponse(responseCode = "400", description = "Invalid paging or sort parameter",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    public PageResponse<UserResponse> list(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String sort,
            @RequestParam(required = false) String direction,
            @RequestParam(required = false) String search) {
        return userService.list(page, size, sort, direction, search);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get user", description = "Fetches one user by id (FR-4).")
    @ApiResponse(responseCode = "200", description = "The user")
    @ApiResponse(responseCode = "404", description = "No such user",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    public UserResponse get(@PathVariable UUID id) {
        return userService.get(id);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update user",
            description = "Full replacement (FR-3) — an omitted phone clears the stored value.")
    @ApiResponse(responseCode = "200", description = "The updated user")
    @ApiResponse(responseCode = "404", description = "No such user",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "409", description = "Email already in use",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    public UserResponse update(@PathVariable UUID id, @Valid @RequestBody UserRequest request) {
        return userService.update(id, request);
    }
}
