package com.example.users.gate;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/** The feature service's answer. Unknown fields are ignored so it can add more. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record FeatureDecision(
        String feature,
        boolean allowed,
        boolean readOnly,
        String deniedMessage,
        List<String> allowedRoles
) {

    /** The fail-open answer used when the feature service cannot be reached. */
    static FeatureDecision allowAll(String feature) {
        return new FeatureDecision(feature, true, false, null, List.of());
    }
}
