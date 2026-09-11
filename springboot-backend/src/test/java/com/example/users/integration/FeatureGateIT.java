package com.example.users.integration;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * How this service behaves against the feature service's answers.
 *
 * <p>Uses a stub rather than the real service: the point is to control what comes
 * back — including nothing at all — which a live dependency cannot do on demand.
 * The stub is a JDK {@code HttpServer}, so no HTTP-mocking library is needed.
 */
@DisplayName("feature gate")
class FeatureGateIT extends AbstractUserIT {

    private static HttpServer stub;
    private static final AtomicReference<String> RESPONSE = new AtomicReference<>();
    private static final AtomicInteger STATUS = new AtomicInteger(200);
    private static final AtomicInteger CALLS = new AtomicInteger();
    private static final AtomicReference<String> LAST_AUTH = new AtomicReference<>();

    @BeforeAll
    static void startStub() throws Exception {
        stub = HttpServer.create(new InetSocketAddress(0), 0);
        stub.createContext("/", exchange -> {
            CALLS.incrementAndGet();
            LAST_AUTH.set(exchange.getRequestHeaders().getFirst("Authorization"));
            byte[] body = RESPONSE.get().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(STATUS.get(), body.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(body);
            }
        });
        stub.start();
    }

    @AfterAll
    static void stopStub() {
        stub.stop(0);
    }

    @DynamicPropertySource
    static void pointAtStub(DynamicPropertyRegistry registry) {
        // Started before the context so the port is known here.
        registry.add("app.feature-service.url", () -> "http://localhost:" + stub.getAddress().getPort());
        registry.add("app.feature-service.cache-ttl-seconds", () -> 0);
    }

    private void answer(int status, boolean allowed, boolean readOnly, String message) {
        STATUS.set(status);
        RESPONSE.set("{\"feature\":\"user-data-access\",\"allowed\":" + allowed
                + ",\"readOnly\":" + readOnly
                + ",\"deniedMessage\":" + (message == null ? "null" : "\"" + message + "\"")
                + ",\"allowedRoles\":[\"ROLE_ADMIN\"]}");
    }

    @Test
    @DisplayName("allowed: the request proceeds")
    void allowedProceeds() {
        answer(200, true, false, null);
        assertThat(send("GET", USERS, adminToken).statusCode()).isEqualTo(200);
    }

    @Test
    @DisplayName("denied: 403 ACCESS_DENIED carrying the service's own message")
    void deniedIsForbidden() {
        answer(200, false, false, "Your role does not grant access to user data.");

        HttpResponse<String> response = send("GET", USERS, adminToken);

        assertThat(response.statusCode()).isEqualTo(403);
        assertThat(json(response).get("code").asString()).isEqualTo("ACCESS_DENIED");
        // The message comes from the feature service, not from a local string.
        assertThat(json(response).get("message").asString())
                .isEqualTo("Your role does not grant access to user data.");
    }

    @Test
    @DisplayName("read-only: reads pass, writes are refused")
    void readOnlyBlocksWrites() {
        answer(200, true, true, null);

        assertThat(send("GET", USERS, adminToken).statusCode()).isEqualTo(200);

        HttpResponse<String> write = send("POST", USERS, adminToken,
                userBody("Jane", "Doe", "jane@example.com", null));
        assertThat(write.statusCode()).isEqualTo(403);
        assertThat(json(write).get("message").asString()).containsIgnoringCase("read-only");
    }

    @Test
    @DisplayName("the caller's own token is forwarded, so the roles evaluated are theirs")
    void forwardsCallerToken() {
        answer(200, true, false, null);
        LAST_AUTH.set(null);

        send("GET", USERS, adminToken);

        assertThat(LAST_AUTH.get()).isEqualTo("Bearer " + adminToken);
    }

    @Test
    @DisplayName("unreachable: fails open rather than taking this service down (FT-7)")
    void unreachableFailsOpen() {
        // Answering 503 stands in for the service being unavailable.
        answer(503, false, false, "should not be used");

        assertThat(send("GET", USERS, adminToken).statusCode()).isEqualTo(200);
    }

    @Test
    @DisplayName("an unparseable answer also fails open, rather than 500-ing")
    void garbageAnswerFailsOpen() {
        STATUS.set(200);
        RESPONSE.set("this is not json");

        assertThat(send("GET", USERS, adminToken).statusCode()).isEqualTo(200);
    }

    @Test
    @DisplayName("the gate is asked on every request when caching is off")
    void gateIsConsulted() {
        answer(200, true, false, null);
        int before = CALLS.get();

        send("GET", USERS, adminToken);
        send("GET", USERS, adminToken);

        assertThat(CALLS.get()).isGreaterThan(before + 1);
    }

    @Test
    @DisplayName("login is never gated — an operator must be able to sign in regardless")
    void loginIsNotGated() {
        answer(200, false, false, "denied");

        HttpResponse<String> response = send("POST", "/api/v1/auth/login", null,
                "{\"username\":\"admin\",\"password\":\"admin123!\"}");

        assertThat(response.statusCode()).isEqualTo(200);
    }
}
