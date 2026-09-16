package com.example.featuretoggle.admin;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * Gates and configures the administrative surface.
 *
 * <p>{@code enabled} is false by default: the console must be switched on
 * deliberately per environment (functional spec FR-11). When off the controller
 * is not registered at all, so its routes 404 rather than 403 — a disabled
 * console should not advertise that it exists.
 */
@ConfigurationProperties(prefix = "app.feature-admin")
public class FeatureAdminProperties {

    private boolean enabled = false;

    /** Features the system depends on; they cannot be deleted or left with no roles (BR-9). */
    private List<String> protectedFeatures = List.of();

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public List<String> getProtectedFeatures() { return protectedFeatures; }
    public void setProtectedFeatures(List<String> protectedFeatures) { this.protectedFeatures = protectedFeatures; }
}
