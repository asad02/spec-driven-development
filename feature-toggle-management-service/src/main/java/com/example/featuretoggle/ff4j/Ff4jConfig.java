package com.example.featuretoggle.ff4j;

import org.ff4j.FF4j;
import org.ff4j.audit.repository.JdbcEventRepository;
import org.ff4j.core.Feature;
import org.ff4j.property.PropertyString;
import org.ff4j.property.store.JdbcPropertyStore;
import org.ff4j.security.AuthorizationsManager;
import org.ff4j.store.JdbcFeatureStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;

import javax.sql.DataSource;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * ff4j over the `feature-toggle` database. Uses ff4j-core's plain-JDBC stores
 * rather than ff4j-store-springjdbc, which is compiled against Spring Framework
 * 6.2 and would drag a second Spring lineage onto a Spring 7 classpath.
 */
@Configuration
public class Ff4jConfig {

    private static final Logger LOG = LoggerFactory.getLogger(Ff4jConfig.class);

    // Without this the bean is built from the DataSource before Flyway has created
    // FF4J_FEATURES, and the first exist() call fails the whole context.
    @Bean
    @DependsOn("flywayInitializer")
    FF4j ff4j(DataSource dataSource, AuthorizationsManager authorizationsManager) {
        FF4j ff4j = new FF4j();
        ff4j.setFeatureStore(new JdbcFeatureStore(dataSource));
        ff4j.setPropertiesStore(new JdbcPropertyStore(dataSource));
        ff4j.setEventRepository(new JdbcEventRepository(dataSource));
        ff4j.setAuthorizationsManager(authorizationsManager);
        ff4j.setAutocreate(false);

        seedRoutingFlag(ff4j);
        seedUserDataAccess(ff4j);
        return ff4j;
    }

    private static void seedRoutingFlag(FF4j ff4j) {
        if (!ff4j.getFeatureStore().exist(FeatureCatalog.BACKEND_ROUTING)) {
            ff4j.getFeatureStore().create(new Feature(FeatureCatalog.BACKEND_ROUTING, false,
                    "Route /api traffic to springboot-user-management-service instead of the Micronaut service"));
            LOG.info("Created feature '{}' (disabled)", FeatureCatalog.BACKEND_ROUTING);
        }
        ensureProperty(ff4j, FeatureCatalog.BACKEND_ROUTING, FeatureCatalog.PROP_ADMIN_ROLE, "ROLE_ADMIN");
        ensureProperty(ff4j, FeatureCatalog.BACKEND_ROUTING, FeatureCatalog.PROP_DENIED_MESSAGE,
                "Switching the active backend requires an administrator role.");
    }

    private static void seedUserDataAccess(FF4j ff4j) {
        if (!ff4j.getFeatureStore().exist(FeatureCatalog.USER_DATA_ACCESS)) {
            Feature feature = new Feature(FeatureCatalog.USER_DATA_ACCESS, true,
                    "Access to /api/v1/users. FF4J_ROLES lists the roles allowed to use it.");
            feature.setPermissions(new LinkedHashSet<>(List.of("ROLE_ADMIN", "ROLE_VIEWER")));
            ff4j.getFeatureStore().create(feature);
            LOG.info("Created feature '{}'", FeatureCatalog.USER_DATA_ACCESS);
        }
        ensureProperty(ff4j, FeatureCatalog.USER_DATA_ACCESS, FeatureCatalog.PROP_READ_ONLY_ROLES, "ROLE_VIEWER");
        ensureProperty(ff4j, FeatureCatalog.USER_DATA_ACCESS, FeatureCatalog.PROP_DENIED_MESSAGE,
                "Your role does not grant access to user data.");
    }

    /** Adds a custom property if absent, leaving an operator-edited value alone. */
    private static void ensureProperty(FF4j ff4j, String uid, String key, String value) {
        Feature feature = ff4j.getFeatureStore().read(uid);
        if (feature.getCustomProperties() != null && feature.getCustomProperties().containsKey(key)) {
            return;
        }
        feature.addProperty(new PropertyString(key, value));
        ff4j.getFeatureStore().update(feature);
        LOG.info("Set custom property {}={} on '{}'", key, value, uid);
    }
}
