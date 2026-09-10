package com.example.users.feature;

import org.ff4j.FF4j;
import org.ff4j.core.Feature;
import org.ff4j.property.PropertyString;
import org.ff4j.security.AuthorizationsManager;
import org.ff4j.store.JdbcFeatureStore;
import org.ff4j.property.store.JdbcPropertyStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.DependsOn;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * ff4j backed by the application's own PostgreSQL database, so a flag flip is a
 * row change visible to every instance immediately — no redeploy, no restart.
 *
 * <p>Uses ff4j-core's plain-JDBC store rather than ff4j-store-springjdbc: the
 * latter is compiled against Spring Framework 6.2 and this runs on Spring 7.
 */
@Configuration
public class Ff4jConfig {

    private static final Logger LOG = LoggerFactory.getLogger(Ff4jConfig.class);

    /** Flag UID. Enabled → route to springboot-backend; disabled → micronaut. */
    public static final String USE_SPRINGBOOT_BACKEND = "use-springboot-backend";

    // Without this the bean is built from the DataSource before Flyway has created
    // FF4J_FEATURES, and the first exist() call fails the whole context.
    @Bean
    @DependsOn("flywayInitializer")
    FF4j ff4j(DataSource dataSource, AuthorizationsManager authorizationsManager) {
        JdbcFeatureStore featureStore = new JdbcFeatureStore(dataSource);
        JdbcPropertyStore propertyStore = new JdbcPropertyStore(dataSource);

        FF4j ff4j = new FF4j();
        ff4j.setFeatureStore(featureStore);
        ff4j.setPropertiesStore(propertyStore);
        // A missing flag must not throw on evaluation; it reads as "off", which is
        // the safe default here (keep routing to the incumbent service).
        ff4j.setAutocreate(true);
        // Makes FF4j.check() consult FF4J_ROLES against the caller's roles.
        ff4j.setAuthorizationsManager(authorizationsManager);

        seedRoutingFlag(ff4j);
        seedUserDataAccess(ff4j);
        return ff4j;
    }

    /**
     * The routing flag. No permissions on purpose — see FeatureAccess.BACKEND_ROUTING.
     * Who may flip it lives in the adminRole custom property, checked by the endpoint.
     */
    private static void seedRoutingFlag(FF4j ff4j) {
        if (!ff4j.exist(FeatureAccess.BACKEND_ROUTING)) {
            Feature feature = new Feature(FeatureAccess.BACKEND_ROUTING, false,
                    "Route /api traffic to springboot-backend instead of the Micronaut service");
            ff4j.createFeature(feature);
            LOG.info("Created feature '{}' (disabled)", FeatureAccess.BACKEND_ROUTING);
        }
        // Backfill rather than skip: the flag predates this policy, and its enabled
        // state must survive being given custom properties.
        ensureProperty(ff4j, FeatureAccess.BACKEND_ROUTING, FeatureAccess.PROP_ADMIN_ROLE, "ROLE_ADMIN");
        ensureProperty(ff4j, FeatureAccess.BACKEND_ROUTING, FeatureAccess.PROP_DENIED_MESSAGE,
                "Switching the active backend requires an administrator role.");
    }

    /** Adds a custom property if absent, leaving an operator-edited value alone. */
    private static void ensureProperty(FF4j ff4j, String featureUid, String key, String value) {
        Feature feature = ff4j.getFeature(featureUid);
        if (feature.getCustomProperties() != null && feature.getCustomProperties().containsKey(key)) {
            return;
        }
        feature.addProperty(new PropertyString(key, value));
        ff4j.getFeatureStore().update(feature);
        LOG.info("Set custom property {}={} on '{}'", key, value, featureUid);
    }

    /**
     * The data gate. Its FF4J_ROLES rows are the allow-list; readOnlyRoles narrows
     * some of those roles to GET only.
     */
    private static void seedUserDataAccess(FF4j ff4j) {
        if (ff4j.exist(FeatureAccess.USER_DATA_ACCESS)) {
            return;
        }
        Feature feature = new Feature(FeatureAccess.USER_DATA_ACCESS, true,
                "Access to /api/v1/users. FF4J_ROLES lists the roles allowed to use it.");
        feature.setPermissions(new LinkedHashSet<>(List.of("ROLE_ADMIN", "ROLE_VIEWER")));
        feature.addProperty(new PropertyString(FeatureAccess.PROP_READ_ONLY_ROLES, "ROLE_VIEWER"));
        feature.addProperty(new PropertyString(FeatureAccess.PROP_DENIED_MESSAGE,
                "Your role does not grant access to user data."));
        ff4j.createFeature(feature);
        LOG.info("Created feature '{}' (roles=ROLE_ADMIN,ROLE_VIEWER readOnly=ROLE_VIEWER)",
                FeatureAccess.USER_DATA_ACCESS);
    }
}
