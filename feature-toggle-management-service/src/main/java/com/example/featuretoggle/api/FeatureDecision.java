package com.example.featuretoggle.api;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * The answer a product backend needs in order to admit or refuse a request.
 *
 * <p>Deliberately a decision, not a feature dump: the caller should not have to
 * re-implement ACL logic, and moving that logic here is the point of extracting
 * this service.
 */
@Schema(name = "FeatureDecision", description = "Whether a caller may use a feature, and how")
public record FeatureDecision(
        String feature,
        boolean allowed,
        boolean readOnly,
        String deniedMessage,
        List<String> allowedRoles
) {
}
