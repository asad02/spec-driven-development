package com.example.users.feature;

import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.ff4j.FF4j;
import org.ff4j.core.Feature;
import org.ff4j.property.Property;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

/** Mirrors the Spring service's evaluation, against the same rows. */
@Singleton
public class FeatureAccessService {

    private static final Logger LOG = LoggerFactory.getLogger(FeatureAccessService.class);

    /** Gates /api/v1/users. FF4J_ROLES rows are the allow-list. */
    public static final String USER_DATA_ACCESS = "user-data-access";
    public static final String PROP_READ_ONLY_ROLES = "readOnlyRoles";
    public static final String PROP_DENIED_MESSAGE = "deniedMessage";

    private final FF4j ff4j;
    private final MicronautAuthorizationsManager authorizations;

    public FeatureAccessService(FF4j ff4j, MicronautAuthorizationsManager authorizations) {
        this.ff4j = ff4j;
        this.authorizations = authorizations;
    }

    /**
     * True when the feature grants this caller access.
     *
     * <p><b>Unconfigured means unrestricted.</b> springboot-backend owns the ff4j
     * schema; if the feature row (or the table) is absent, no policy has been
     * expressed and this service does not invent one. That is deliberately
     * fail-open, and it is the right trade here: this is an authorisation layer
     * *on top of* authentication that has already succeeded, so a feature-store
     * outage must not lock every operator out of a working service. Denials come
     * from a policy that exists and excludes you, never from a missing lookup.
     */
    public boolean isAllowed(String featureUid) {
        try {
            return !ff4j.exist(featureUid) || ff4j.check(featureUid);
        } catch (RuntimeException ex) {
            LOG.warn("Feature store unavailable for '{}'; allowing (no policy could be read)", featureUid, ex);
            return true;
        }
    }

    public boolean isReadOnly(String featureUid) {
        Set<String> readOnly = csvProperty(featureUid, PROP_READ_ONLY_ROLES);
        Set<String> mine = authorizations.getCurrentUserPermissions();
        return !mine.isEmpty() && readOnly.containsAll(mine);
    }

    public String deniedMessage(String featureUid, String fallback) {
        String message = stringProperty(featureUid, PROP_DENIED_MESSAGE);
        return message == null ? fallback : message;
    }

    private Set<String> csvProperty(String featureUid, String key) {
        String raw = stringProperty(featureUid, key);
        if (raw == null || raw.isBlank()) {
            return Set.of();
        }
        return Arrays.stream(raw.split(",")).map(String::trim).filter(v -> !v.isEmpty())
                .collect(Collectors.toUnmodifiableSet());
    }

    private String stringProperty(String featureUid, String key) {
        try {
            if (!ff4j.exist(featureUid)) {
                return null;
            }
        } catch (RuntimeException ex) {
            return null;
        }
        Feature feature = ff4j.getFeature(featureUid);
        Property<?> property = feature.getCustomProperties() == null
                ? null
                : feature.getCustomProperties().get(key);
        return property == null || property.getValue() == null ? null : String.valueOf(property.getValue());
    }
}
