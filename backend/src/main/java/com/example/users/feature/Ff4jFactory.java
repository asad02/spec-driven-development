package com.example.users.feature;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import io.micronaut.context.annotation.Context;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Value;
import jakarta.inject.Singleton;
import org.ff4j.FF4j;
import org.ff4j.property.store.JdbcPropertyStore;
import org.ff4j.security.AuthorizationsManager;
import org.ff4j.store.JdbcFeatureStore;

import javax.sql.DataSource;

/**
 * Reads the feature store that springboot-backend owns and migrates. This build
 * never creates those tables; it only reads the ACL and enforces it.
 *
 * <p>ff4j gets its <b>own</b> small pool rather than the application DataSource.
 * The injected bean hands back Micronaut Data's contextual connection, whose
 * close()/isClosed() throw outside a @Connectable or @Transactional scope — and
 * ff4j closes every connection it opens. A separate pool also keeps flag reads out
 * of business transactions, which is the correct boundary anyway.
 */
@Factory
public class Ff4jFactory {

    /**
     * Deliberately NOT a bean. Micronaut Data wraps every DataSource bean it sees
     * with connection-context advice, so exposing this one would reintroduce the
     * contextual connection the comment above is about. Constructed inline instead.
     */
    private static DataSource ff4jDataSource(String url, String username, String password) {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(url);
        config.setUsername(username);
        config.setPassword(password);
        config.setPoolName("ff4j-pool");
        // Flag lookups are short and infrequent; two connections is ample.
        config.setMaximumPoolSize(2);
        config.setMinimumIdle(1);
        config.setReadOnly(true);
        return new HikariDataSource(config);
    }

    @Singleton
    @Context
    FF4j ff4j(@Value("${datasources.default.url}") String url,
              @Value("${datasources.default.username}") String username,
              @Value("${datasources.default.password}") String password,
              AuthorizationsManager authorizationsManager) {
        DataSource ff4jDataSource = ff4jDataSource(url, username, password);
        FF4j ff4j = new FF4j();
        ff4j.setFeatureStore(new JdbcFeatureStore(ff4jDataSource));
        ff4j.setPropertiesStore(new JdbcPropertyStore(ff4jDataSource));
        ff4j.setAuthorizationsManager(authorizationsManager);
        // A feature this service has never heard of must not throw mid-request.
        ff4j.setAutocreate(false);
        return ff4j;
    }
}
