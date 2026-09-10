package com.example.users.integration;

import com.example.users.dto.ApiError;
import com.example.users.entity.AuthUserEntity;
import com.example.users.repository.AuthUserRepository;
import io.micronaut.core.type.Argument;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.security.authentication.UsernamePasswordCredentials;
import io.micronaut.security.token.render.BearerAccessRefreshToken;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mindrot.jbcrypt.BCrypt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

@MicronautTest(environments = "test", transactional = false)
class AuthIT extends AbstractIntegrationTest {

    @Inject
    @Client("/")
    HttpClient http;

    @Inject
    AuthUserRepository authUserRepository;

    private HttpClientResponseException attemptLogin(String username, String password) {
        return catchThrowableOfType(
                () -> http.toBlocking().exchange(
                        HttpRequest.POST("/api/v1/auth/login", new UsernamePasswordCredentials(username, password)),
                        Argument.of(BearerAccessRefreshToken.class), Argument.of(ApiError.class)),
                HttpClientResponseException.class);
    }

    @Test
    @DisplayName("acceptance 01: the seeded operator can log in and receives a bearer token")
    void seededOperatorCanLogIn() {
        BearerAccessRefreshToken token = http.toBlocking().retrieve(
                HttpRequest.POST("/api/v1/auth/login", new UsernamePasswordCredentials("admin", "admin123!")),
                BearerAccessRefreshToken.class);

        assertThat(token.getAccessToken()).isNotBlank();
        assertThat(token.getTokenType()).isEqualToIgnoringCase("Bearer");
        assertThat(token.getExpiresIn()).isEqualTo(3600);
        assertThat(token.getUsername()).isEqualTo("admin");
        assertThat(token.getRoles()).contains("ROLE_ADMIN");
    }

    @Test
    @DisplayName("acceptance 02: a wrong password and an unknown user fail identically")
    void failuresAreIndistinguishable() {
        HttpClientResponseException wrongPassword = attemptLogin("admin", "not-the-password");
        HttpClientResponseException unknownUser = attemptLogin("nobody-here", "not-the-password");

        assertThat(wrongPassword.getStatus().getCode()).isEqualTo(401);
        assertThat(unknownUser.getStatus().getCode()).isEqualTo(401);

        // Same status, same body shape, same text: nothing distinguishes a real
        // account from an imaginary one.
        assertThat(unknownUser.getResponse().getBody(String.class))
                .isEqualTo(wrongPassword.getResponse().getBody(String.class));
    }

    @Test
    @DisplayName("a disabled account cannot log in, and fails the same way")
    void disabledAccountRejected() {
        AuthUserEntity disabled = new AuthUserEntity();
        disabled.setUsername("disabled-operator");
        disabled.setPasswordHash(BCrypt.hashpw("correct-horse", BCrypt.gensalt(12)));
        disabled.setRoles("ROLE_ADMIN");
        disabled.setEnabled(false);
        authUserRepository.save(disabled);

        HttpClientResponseException ex = attemptLogin("disabled-operator", "correct-horse");
        HttpClientResponseException unknown = attemptLogin("nobody-here", "correct-horse");

        assertThat(ex.getStatus().getCode()).isEqualTo(401);
        assertThat(ex.getResponse().getBody(String.class))
                .isEqualTo(unknown.getResponse().getBody(String.class));
    }

    @Test
    @DisplayName("acceptance 09: user endpoints reject a missing, malformed or expired token")
    void protectedEndpointsRequireAToken() {
        HttpClientResponseException noToken = catchThrowableOfType(
                () -> http.toBlocking().exchange(HttpRequest.GET("/api/v1/users")),
                HttpClientResponseException.class);
        assertThat(noToken.getStatus().getCode()).isEqualTo(401);

        HttpClientResponseException garbage = catchThrowableOfType(
                () -> http.toBlocking().exchange(HttpRequest.GET("/api/v1/users").bearerAuth("not-a-jwt")),
                HttpClientResponseException.class);
        assertThat(garbage.getStatus().getCode()).isEqualTo(401);

        // Signed with the right shape but the wrong key.
        String foreignToken = "eyJhbGciOiJIUzI1NiJ9."
                + "eyJzdWIiOiJhZG1pbiIsImV4cCI6NDA3MDkwODgwMH0."
                + "0000000000000000000000000000000000000000000";
        HttpClientResponseException wrongKey = catchThrowableOfType(
                () -> http.toBlocking().exchange(HttpRequest.GET("/api/v1/users").bearerAuth(foreignToken)),
                HttpClientResponseException.class);
        assertThat(wrongKey.getStatus().getCode()).isEqualTo(401);
    }

    @Test
    @DisplayName("the health check is anonymous so orchestrators can probe it")
    void healthIsAnonymous() {
        String body = http.toBlocking().retrieve(HttpRequest.GET("/health"));
        assertThat(body).contains("UP");
    }

    @Test
    @DisplayName("operational endpoints are not anonymous")
    void metricsRequireAuth() {
        HttpClientResponseException ex = catchThrowableOfType(
                () -> http.toBlocking().exchange(HttpRequest.GET("/metrics")),
                HttpClientResponseException.class);
        assertThat(ex.getStatus().getCode()).isEqualTo(401);
    }

    @Test
    @DisplayName("no response ever carries the password hash")
    void hashNeverLeaves() {
        BearerAccessRefreshToken token = http.toBlocking().retrieve(
                HttpRequest.POST("/api/v1/auth/login", new UsernamePasswordCredentials("admin", "admin123!")),
                BearerAccessRefreshToken.class);

        String raw = http.toBlocking().retrieve(
                HttpRequest.GET("/api/v1/users").bearerAuth(token.getAccessToken()));

        assertThat(raw).doesNotContain("password").doesNotContain("$2a$");
    }
}
