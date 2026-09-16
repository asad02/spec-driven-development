package com.example.users.integration;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Singleton container, started once for the JVM. {@code @Testcontainers} would
 * stop and restart it between classes, which also defeats Spring's test context
 * cache — every class would pay a container start and a context refresh.
 *
 * <p>RANDOM_PORT is not optional: the development stack publishes 8080 and 8082,
 * so a fixed test port fails depending on what is already running.
 *
 * <p>HTTP goes through the JDK client. Spring Boot 4 removed
 * {@code TestRestTemplate}, and these tests assert status codes — including 403s
 * and 405s — which the JDK client reports plainly instead of throwing.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
abstract class AbstractUserIT {

    // PostgreSQLContainer is not generic in Testcontainers 2.x.
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");

    static {
        POSTGRES.start();
    }

    protected static final String USERS = "/api/v1/users";
    protected static final ObjectMapper JSON = JsonMapper.builder().build();

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10)).build();

    @LocalServerPort
    protected int port;

    @Autowired
    protected JdbcTemplate jdbc;

    protected String adminToken;

    @BeforeEach
    void signInAndClear() {
        // Each test starts from an empty table; isolation by truncation rather than
        // by restarting a container.
        jdbc.execute("TRUNCATE TABLE users");
        adminToken = token("admin", "admin123!");
    }

    protected String token(String username, String password) {
        HttpResponse<String> response = send("POST", "/api/v1/auth/login", null,
                "{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}");
        JsonNode body = json(response);
        return body.has("access_token") ? body.get("access_token").asString() : null;
    }

    /** @param token null sends no Authorization header, for the anonymous cases. */
    protected HttpResponse<String> send(String method, String path, String token, String body) {
        try {
            HttpRequest.BodyPublisher publisher = body == null
                    ? HttpRequest.BodyPublishers.noBody()
                    : HttpRequest.BodyPublishers.ofString(body);
            HttpRequest.Builder request = HttpRequest.newBuilder()
                    .uri(URI.create("http://localhost:" + port + path))
                    .timeout(Duration.ofSeconds(20))
                    .header("Content-Type", "application/json")
                    .method(method, publisher);
            if (token != null) {
                request.header("Authorization", "Bearer " + token);
            }
            return http.send(request.build(), HttpResponse.BodyHandlers.ofString());
        } catch (Exception e) {
            throw new IllegalStateException(method + " " + path + " failed", e);
        }
    }

    protected HttpResponse<String> send(String method, String path, String token) {
        return send(method, path, token, null);
    }

    protected static JsonNode json(HttpResponse<String> response) {
        return JSON.readTree(response.body());
    }

    protected static String userBody(String first, String last, String email, String phone) {
        return "{\"firstName\":\"" + first + "\",\"lastName\":\"" + last + "\",\"email\":\"" + email + "\""
                + (phone == null ? "" : ",\"phone\":\"" + phone + "\"") + "}";
    }

    /** Creates a user and returns its id. */
    protected String createUser(String first, String last, String email) {
        HttpResponse<String> response = send("POST", USERS, adminToken, userBody(first, last, email, null));
        if (response.statusCode() != 201) {
            throw new IllegalStateException("create failed: " + response.statusCode() + " " + response.body());
        }
        return json(response).get("id").asString();
    }
}
