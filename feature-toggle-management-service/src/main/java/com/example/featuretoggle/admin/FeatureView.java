package com.example.featuretoggle.admin;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;
import java.util.Map;

/** How a feature is presented to the console. */
@Schema(name = "Feature")
public record FeatureView(
        String uid,
        boolean enabled,
        String description,
        List<String> roles,
        Map<String, String> properties,
        /* Server-computed. A client cannot set or clear it; supplying it is ignored. */
        boolean isProtected,
        String protectedReason
) {
}
