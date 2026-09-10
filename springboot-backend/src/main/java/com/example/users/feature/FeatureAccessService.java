package com.example.users.feature;

import org.ff4j.FF4j;
import org.ff4j.core.Feature;
import org.ff4j.property.Property;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.Set;

/**
 * Answers "may this caller do this?" from the ff4j tables — FF4J_ROLES for the
 * allow-list, FF4J_CUSTOM_PROPERTIES for the policy the allow-list cannot express.
 */
@Service
public class FeatureAccessService {

    private final FF4j ff4j;
    private final SpringAuthorizationsManager authorizations;

    public FeatureAccessService(FF4j ff4j, SpringAuthorizationsManager authorizations) {
        this.ff4j = ff4j;
        this.authorizations = authorizations;
    }

    /**
     * True when the feature is enabled AND the caller holds one of its FF4J_ROLES.
     * ff4j.check() performs both halves via the AuthorizationsManager.
     */
    public boolean isAllowed(String featureUid) {
        return ff4j.exist(featureUid) && ff4j.check(featureUid);
    }

    /** Roles listed in the feature's readOnlyRoles custom property. */
    public boolean isReadOnly(String featureUid) {
        Set<String> readOnly = csvProperty(featureUid, FeatureAccess.PROP_READ_ONLY_ROLES);
        Set<String> mine = authorizations.getCurrentUserPermissions();
        // Read-only only if EVERY role the caller holds is a read-only role; someone
        // who is both viewer and admin keeps write access.
        return !mine.isEmpty() && readOnly.containsAll(mine);
    }

    /** True when the caller holds the feature's adminRole custom property value. */
    public boolean isAdminFor(String featureUid) {
        Set<String> adminRoles = csvProperty(featureUid, FeatureAccess.PROP_ADMIN_ROLE);
        if (adminRoles.isEmpty()) {
            return true;
        }
        return authorizations.getCurrentUserPermissions().stream().anyMatch(adminRoles::contains);
    }

    public String deniedMessage(String featureUid, String fallback) {
        String message = stringProperty(featureUid, FeatureAccess.PROP_DENIED_MESSAGE);
        return message == null ? fallback : message;
    }

    /** Roles allowed by the feature's ACL, for display and diagnostics. */
    public Set<String> allowedRoles(String featureUid) {
        if (!ff4j.exist(featureUid)) {
            return Set.of();
        }
        Set<String> permissions = ff4j.getFeature(featureUid).getPermissions();
        return permissions == null ? Set.of() : permissions;
    }

    private Set<String> csvProperty(String featureUid, String key) {
        String raw = stringProperty(featureUid, key);
        if (raw == null || raw.isBlank()) {
            return Set.of();
        }
        return Arrays.stream(raw.split(",")).map(String::trim).filter(v -> !v.isEmpty())
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    private String stringProperty(String featureUid, String key) {
        if (!ff4j.exist(featureUid)) {
            return null;
        }
        Feature feature = ff4j.getFeature(featureUid);
        Property<?> property = feature.getCustomProperties() == null
                ? null
                : feature.getCustomProperties().get(key);
        return property == null || property.getValue() == null ? null : String.valueOf(property.getValue());
    }
}
