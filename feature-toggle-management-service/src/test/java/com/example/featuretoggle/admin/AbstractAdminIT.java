package com.example.featuretoggle.admin;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSSigner;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Date;
import java.util.List;

/**
 * One PostgreSQL container for the suite, wired in by {@code @ServiceConnection} —
 * no property plumbing, and none of the lifecycle traps the Micronaut suite needed.
 *
 * <p>RANDOM_PORT is not optional: the development stack publishes 8080 and 8082, so
 * a fixed test port fails depending on what is already running.
 *
 * <p>HTTP goes through the JDK client rather than a Spring test client. Spring Boot 4
 * removed {@code TestRestTemplate}, and these tests assert status codes — including
 * 403s — which the JDK client reports plainly instead of throwing.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
abstract class AbstractAdminIT {

    /*
     * Singleton, started once for the JVM rather than per class. @Testcontainers
     * would stop and restart it between classes, which also defeats Spring's test
     * context cache — every class would pay a container start and a context refresh.
     * Started here, all classes share one container and one context.
     *
     * PostgreSQLContainer is not generic in Testcontainers 2.x.
     */
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");

    static {
        POSTGRES.start();
    }

    protected static final String FEATURES = "/api/v1/admin/features";
    protected static final String PROTECTED_FEATURE = "user-data-access";

    protected static final ObjectMapper JSON = JsonMapper.builder().build();

    /** Must match app.jwt.secret in application-test.yml. */
    private static final String SECRET = "test-only-signature-secret-at-least-256-bits-long-for-hs256-ok";

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10)).build();

    @LocalServerPort
    protected int port;

    protected String adminToken;
    protected String viewerToken;
    protected String noAccessToken;

    @BeforeEach
    void signIn() {
        adminToken = token("admin", "ROLE_ADMIN");
        viewerToken = token("viewer", "ROLE_VIEWER");
        noAccessToken = token("noaccess", "ROLE_NONE");
    }

    /**
     * Mints a token locally. This service has no login endpoint — it validates
     * tokens the product backends issue — so the suite signs its own with the
     * shared secret rather than depending on another service being up.
     */
    protected String token(String username, String... roles) {
        try {
            JWSSigner signer = new MACSigner(SECRET.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            JWTClaimsSet claims = new JWTClaimsSet.Builder()
                    .subject(username)
                    .issuer("user-service")
                    .claim("roles", List.of(roles))
                    .issueTime(new Date())
                    .expirationTime(new Date(System.currentTimeMillis() + 3_600_000))
                    .build();
            SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
            jwt.sign(signer);
            return jwt.serialize();
        } catch (Exception e) {
            throw new IllegalStateException("could not mint a test token", e);
        }
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

    protected String createFeature(String uid) {
        HttpResponse<String> response = send("POST", FEATURES, adminToken,
                "{\"uid\":\"" + uid + "\",\"description\":\"created by tests\",\"enabled\":false,\"roles\":[]}");
        if (response.statusCode() != 201) {
            throw new IllegalStateException("could not create " + uid + ": " + response.body());
        }
        return uid;
    }
}
