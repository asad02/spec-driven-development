package com.example.users.integration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.net.http.HttpResponse;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Framework-level failures must carry the documented status and envelope, not be
 * flattened into 500 by the catch-all handler.
 *
 * <p>This suite exists because a catch-all {@code @ExceptionHandler(Throwable.class)}
 * once swallowed all of these — including DELETE on a user, which the Micronaut
 * service answers with 405 and this one answered with 500, breaking D-13. None of
 * it is covered by the Postman collection.
 */
@DisplayName("error contract: framework failures")
class ErrorContractIT extends AbstractUserIT {

    @Test
    @DisplayName("DELETE on a user is 405 — the same answer the Micronaut service gives")
    void deleteUserIsMethodNotAllowed() {
        HttpResponse<String> response = send("DELETE",
                USERS + "/00000000-0000-0000-0000-000000000000", adminToken);

        assertThat(response.statusCode()).isEqualTo(405);
        assertThat(json(response).get("code").asString()).isEqualTo("METHOD_NOT_ALLOWED");
    }

    @Test
    @DisplayName("a malformed JSON body is 400, not a server fault")
    void malformedBodyIsBadRequest() {
        HttpResponse<String> response = send("POST", USERS, adminToken, "{not json");

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(json(response).get("code").asString()).isEqualTo("MALFORMED_REQUEST");
    }

    @Test
    @DisplayName("an unsupported content type is 415")
    void wrongContentTypeIsUnsupportedMediaType() {
        HttpResponse<String> response = sendWithContentType("POST", USERS, adminToken,
                "plain text", "text/plain");

        assertThat(response.statusCode()).isEqualTo(415);
        assertThat(json(response).get("code").asString()).isEqualTo("UNSUPPORTED_MEDIA_TYPE");
    }

    @Test
    @DisplayName("an unparseable path variable is 400, not 500")
    void badUuidIsBadRequest() {
        HttpResponse<String> response = send("GET", USERS + "/not-a-uuid", adminToken);

        assertThat(response.statusCode()).isBetween(400, 404);
        assertThat(response.statusCode()).isNotEqualTo(500);
    }

    @Test
    @DisplayName("every failure carries the correlation id, so a report can be traced")
    void failuresCarryCorrelationId() {
        HttpResponse<String> response = send("DELETE",
                USERS + "/00000000-0000-0000-0000-000000000000", adminToken);

        assertThat(json(response).get("correlationId").asString()).isNotEmpty();
        assertThat(response.headers().firstValue("X-Correlation-Id")).isPresent();
    }

    private HttpResponse<String> sendWithContentType(String method, String path, String token,
                                                     String body, String contentType) {
        try {
            java.net.http.HttpRequest request = java.net.http.HttpRequest.newBuilder()
                    .uri(java.net.URI.create("http://localhost:" + port + path))
                    .timeout(java.time.Duration.ofSeconds(20))
                    .header("Content-Type", contentType)
                    .header("Authorization", "Bearer " + token)
                    .method(method, java.net.http.HttpRequest.BodyPublishers.ofString(body))
                    .build();
            return java.net.http.HttpClient.newHttpClient()
                    .send(request, HttpResponse.BodyHandlers.ofString());
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
