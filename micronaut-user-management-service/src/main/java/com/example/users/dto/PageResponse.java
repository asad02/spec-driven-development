package com.example.users.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.micronaut.core.annotation.Introspected;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * Our own page envelope rather than the framework's, so the wire format cannot
 * shift under a Micronaut upgrade (technical spec §6.4).
 */
@Introspected
// Micronaut's Jackson default inclusion is NON_EMPTY, which drops `content` from the
// payload entirely when a page is empty - clients then see null instead of []. The
// envelope's shape must not depend on how many rows came back.
@JsonInclude(JsonInclude.Include.ALWAYS)
@Schema(name = "UserPage", description = "A page of user records")
public record PageResponse<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        String sort,
        String direction
) {
}
