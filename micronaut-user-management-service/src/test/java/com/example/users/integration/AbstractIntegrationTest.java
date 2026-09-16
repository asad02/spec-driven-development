package com.example.users.integration;

import io.micronaut.test.support.TestPropertyProvider;
import org.junit.jupiter.api.TestInstance;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.io.IOException;
import java.net.ServerSocket;
import java.util.Map;

/**
 * One PostgreSQL container for the whole suite, started once and reused. Tests
 * isolate themselves by clearing data, not by restarting the container.
 *
 * <p>The container is the only database the tests can reach — there is no path
 * from here to a shared or developer database.
 *
 * <p>PER_CLASS is required, not stylistic: micronaut-test resolves
 * TestPropertyProvider from {@code context.getTestInstance()} during {@code beforeAll},
 * and under JUnit's default per-method lifecycle no instance exists yet, so
 * {@link #getProperties()} is silently skipped and the container URL below never
 * reaches the application context.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public abstract class AbstractIntegrationTest implements TestPropertyProvider {

    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:17-alpine"))
                    .withDatabaseName("usersdb")
                    .withUsername("test")
                    .withPassword("test")
                    .withReuse(false);

    static {
        POSTGRES.start();
    }

    private static int freePort() {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        } catch (IOException e) {
            throw new IllegalStateException("No free port for the test server", e);
        }
    }

    @Override
    public Map<String, String> getProperties() {
        if (!POSTGRES.isRunning()) {
            POSTGRES.start();
        }
        return Map.of(
                "datasources.default.url", POSTGRES.getJdbcUrl(),
                "datasources.default.username", POSTGRES.getUsername(),
                "datasources.default.password", POSTGRES.getPassword(),
                // Both micronaut.server.port and endpoints.all.port read ${SERVER_PORT:8080}
                // in application.yml. A concrete free port keeps them on the SAME port -
                // "-1" would give each its own random one and move /health off the app port.
                "SERVER_PORT", String.valueOf(freePort())
        );
    }
}
