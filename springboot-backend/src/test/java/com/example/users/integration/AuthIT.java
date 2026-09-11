package com.example.users.integration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

import java.net.http.HttpResponse;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("authentication")
class AuthIT extends AbstractUserIT {

    @Test
    @DisplayName("login returns the exact shape the SPA and Postman collection expect")
    void loginShape() {
        HttpResponse<String> response = send("POST", "/api/v1/auth/login", null,
                "{\"username\":\"admin\",\"password\":\"admin123!\"}");

        assertThat(response.statusCode()).isEqualTo(200);
        JsonNode body = json(response);
        // snake_case on access_token: this mirrors Micronaut's BearerAccessRefreshToken
        // and both the SPA and the collection depend on it verbatim.
        assertThat(body.get("access_token").asString()).isNotEmpty();
        assertThat(body.get("token_type").asString()).isEqualTo("Bearer");
        assertThat(body.get("expires_in").asLong()).isEqualTo(3600);
        assertThat(body.get("username").asString()).isEqualTo("admin");
        // Only ROLE_*: Spring Security 7 also grants factor authorities, which are
        // an internal detail and must not leak into the response.
        assertThat(body.get("roles").toString()).isEqualTo("[\"ROLE_ADMIN\"]");
    }

    @Test
    @DisplayName("a wrong password and an unknown user are indistinguishable")
    void badCredentialsAreIndistinguishable() {
        HttpResponse<String> wrongPassword = send("POST", "/api/v1/auth/login", null,
                "{\"username\":\"admin\",\"password\":\"wrong\"}");
        HttpResponse<String> unknownUser = send("POST", "/api/v1/auth/login", null,
                "{\"username\":\"nobody\",\"password\":\"wrong\"}");

        assertThat(wrongPassword.statusCode()).isEqualTo(401);
        assertThat(unknownUser.statusCode()).isEqualTo(401);
        // Same body as well as same status — the shape must not leak existence either.
        assertThat(json(wrongPassword).get("code").asString())
                .isEqualTo(json(unknownUser).get("code").asString());
        assertThat(json(wrongPassword).get("message").asString())
                .isEqualTo(json(unknownUser).get("message").asString());
    }

    @Test
    @DisplayName("a disabled account is refused, and says no more than the others")
    void disabledAccountIsRefused() {
        HttpResponse<String> response = send("POST", "/api/v1/auth/login", null,
                "{\"username\":\"disabled\",\"password\":\"admin123!\"}");

        assertThat(response.statusCode()).isEqualTo(401);
        assertThat(json(response).get("message").asString()).doesNotContainIgnoringCase("disabled");
    }

    @Test
    @DisplayName("user endpoints require a token")
    void userEndpointsRequireToken() {
        assertThat(send("GET", USERS, null).statusCode()).isEqualTo(401);
        assertThat(send("POST", USERS, null, userBody("A", "B", "a@example.com", null)).statusCode())
                .isEqualTo(401);
    }

    @Test
    @DisplayName("a malformed or foreign token is rejected, not accepted unverified")
    void badTokenRejected() {
        assertThat(send("GET", USERS, "not.a.token").statusCode()).isEqualTo(401);
        // Correctly formed JWT signed with a different secret.
        String foreign = "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJhZG1pbiIsInJvbGVzIjpbIlJPTEVfQURNSU4iXX0"
                + ".XXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX";
        assertThat(send("GET", USERS, foreign).statusCode()).isEqualTo(401);
    }

    @Test
    @DisplayName("the health check is anonymous so orchestrators can probe it")
    void healthIsAnonymous() {
        HttpResponse<String> response = send("GET", "/health", null);

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("UP");
    }

    @Test
    @DisplayName("unauthenticated is 401, not 403 — the distinction matters to the UI")
    void unauthenticatedIsNotForbidden() {
        assertThat(send("GET", USERS, null).statusCode()).isEqualTo(401);
    }
}
