package com.example.users.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * Our own page envelope rather than Spring Data's {@code PageImpl}, whose JSON
 * shape is explicitly unstable across versions (technical spec §6.4).
 *
 * <p>ALWAYS is redundant under Spring's Jackson defaults but is stated anyway:
 * `content` must be present as `[]` on an empty page, and that guarantee should
 * not depend on a framework default staying put.
 */
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
