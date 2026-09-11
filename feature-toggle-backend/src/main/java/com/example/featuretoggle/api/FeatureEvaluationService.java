package com.example.featuretoggle.api;

import com.example.featuretoggle.ff4j.CallerRoles;
import com.example.featuretoggle.ff4j.FeatureCatalog;
import dev.openfeature.sdk.Client;
import org.ff4j.FF4j;
import org.ff4j.core.Feature;
import org.ff4j.property.Property;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The single place access decisions are made. Product backends call this rather
 * than reading the store, so the rules cannot drift between implementations.
 */
@Service
public class FeatureEvaluationService {

    private final FF4j ff4j;
    private final Client featureClient;
    private final CallerRoles callerRoles;

    public FeatureEvaluationService(FF4j ff4j, Client featureClient, CallerRoles callerRoles) {
        this.ff4j = ff4j;
        this.featureClient = featureClient;
        this.callerRoles = callerRoles;
    }

    /** Which product backend should serve /api right now. */
    public BackendProvider activeBackend() {
        boolean useSpring = featureClient.getBooleanValue(FeatureCatalog.BACKEND_ROUTING, false);
        return useSpring ? BackendProvider.SPRINGBOOT : BackendProvider.MICRONAUT;
    }

    public BackendProvider setActiveBackend(BackendProvider provider) {
        if (provider == BackendProvider.SPRINGBOOT) {
            ff4j.enable(FeatureCatalog.BACKEND_ROUTING);
        } else {
            ff4j.disable(FeatureCatalog.BACKEND_ROUTING);
        }
        return activeBackend();
    }

    /**
     * Decides whether the authenticated caller may use a feature.
     *
     * <p>A feature that does not exist is <em>allowed</em>: this service is the
     * owner, but a product backend asking about a flag nobody has configured should
     * not have its traffic refused by an absence. Refusals come from a policy that
     * exists and excludes the caller.
     */
    public FeatureDecision decide(String featureUid) {
        if (!ff4j.getFeatureStore().exist(featureUid)) {
            return new FeatureDecision(featureUid, true, false, null, List.of());
        }
        boolean allowed = ff4j.check(featureUid);
        Feature feature = ff4j.getFeatureStore().read(featureUid);
        Set<String> permissions = feature.getPermissions() == null ? Set.of() : feature.getPermissions();

        return new FeatureDecision(
                featureUid,
                allowed,
                allowed && isReadOnly(feature),
                allowed ? null : deniedMessage(feature),
                permissions.stream().sorted().toList());
    }

    /** True when every role the caller holds is listed as read-only. */
    private boolean isReadOnly(Feature feature) {
        Set<String> readOnly = csv(feature, FeatureCatalog.PROP_READ_ONLY_ROLES);
        Set<String> mine = callerRoles.getCurrentUserPermissions();
        return !mine.isEmpty() && readOnly.containsAll(mine);
    }

    /** True when the caller holds the feature's adminRole property value. */
    public boolean isAdminFor(String featureUid) {
        if (!ff4j.getFeatureStore().exist(featureUid)) {
            return true;
        }
        Set<String> adminRoles = csv(ff4j.getFeatureStore().read(featureUid), FeatureCatalog.PROP_ADMIN_ROLE);
        return adminRoles.isEmpty()
                || callerRoles.getCurrentUserPermissions().stream().anyMatch(adminRoles::contains);
    }

    public String deniedMessage(String featureUid, String fallback) {
        if (!ff4j.getFeatureStore().exist(featureUid)) {
            return fallback;
        }
        String message = deniedMessage(ff4j.getFeatureStore().read(featureUid));
        return message == null ? fallback : message;
    }

    private static String deniedMessage(Feature feature) {
        return property(feature, FeatureCatalog.PROP_DENIED_MESSAGE);
    }

    private static Set<String> csv(Feature feature, String key) {
        String raw = property(feature, key);
        if (raw == null || raw.isBlank()) {
            return Set.of();
        }
        return Arrays.stream(raw.split(",")).map(String::trim).filter(v -> !v.isEmpty())
                .collect(Collectors.toUnmodifiableSet());
    }

    private static String property(Feature feature, String key) {
        Property<?> property = feature.getCustomProperties() == null
                ? null : feature.getCustomProperties().get(key);
        return property == null || property.getValue() == null ? null : String.valueOf(property.getValue());
    }
}
