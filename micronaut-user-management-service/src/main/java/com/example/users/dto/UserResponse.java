package com.example.users.dto;

import com.example.users.entity.UserEntity;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.core.annotation.Nullable;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

@Introspected
// Micronaut's Jackson default inclusion is NON_EMPTY, which drops `phone` entirely
// when it is null — clients then see the key missing rather than null, and the two
// backend implementations disagree on the shape. The envelope must not depend on
// whether an optional field happens to be set.
@JsonInclude(JsonInclude.Include.ALWAYS)
@Schema(name = "User", description = "A stored user record")
public record UserResponse(
        UUID id,
        String firstName,
        String lastName,
        String email,
        @Nullable String phone,
        Instant createdAt,
        Instant updatedAt
) {

    public static UserResponse from(UserEntity e) {
        return new UserResponse(
                e.getId(),
                e.getFirstName(),
                e.getLastName(),
                e.getEmail(),
                e.getPhone(),
                e.getCreatedAt(),
                e.getUpdatedAt()
        );
    }
}
