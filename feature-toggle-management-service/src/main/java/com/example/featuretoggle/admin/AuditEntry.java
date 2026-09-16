package com.example.featuretoggle.admin;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

@Schema(name = "AuditEntry")
public record AuditEntry(
        Instant at,
        String user,
        String action,
        String feature,
        String detail
) {
}
