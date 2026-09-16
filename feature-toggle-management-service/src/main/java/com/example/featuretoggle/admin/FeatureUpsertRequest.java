package com.example.featuretoggle.admin;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

@Schema(name = "FeatureUpsertRequest")
public record FeatureUpsertRequest(
        @NotBlank
        @Size(max = 100)
        // ff4j stores the uid as the primary key; keep it to characters that are
        // safe in a URL path segment so /features/{uid} needs no encoding.
        @Pattern(regexp = "^[A-Za-z0-9._-]+$", message = "must contain only letters, digits, dot, dash or underscore")
        @Schema(example = "new-checkout-flow") String uid,

        @Size(max = 1000) String description,
        boolean enabled,
        List<String> roles
) {
}
