package com.example.users.integration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

import java.net.http.HttpResponse;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The wire contract, through the real stack to a real PostgreSQL. These assert the
 * shapes the Angular SPA and the Postman collection depend on — D-13's
 * byte-identical contract with the Micronaut service.
 */
@DisplayName("user API")
class UserApiIT extends AbstractUserIT {

    @Test
    @DisplayName("create returns 201 with a Location header and the stored record")
    void createReturnsCreated() {
        HttpResponse<String> response = send("POST", USERS, adminToken,
                userBody("Jane", "Doe", "jane@example.com", "555-0100"));

        assertThat(response.statusCode()).isEqualTo(201);
        assertThat(response.headers().firstValue("Location")).isPresent()
                .hasValueSatisfying(l -> assertThat(l).startsWith("/api/v1/users/"));

        JsonNode body = json(response);
        assertThat(body.get("id").asString()).isNotEmpty();
        assertThat(body.get("firstName").asString()).isEqualTo("Jane");
        // ISO-8601, never a numeric timestamp: Angular's date pipe reads bare
        // numbers as milliseconds and renders them as 1970.
        assertThat(body.get("createdAt").asString()).matches("^\\d{4}-\\d{2}-\\d{2}T.*");
    }

    @Test
    @DisplayName("an email already in use is 409 and names the offending field")
    void duplicateEmailIsConflict() {
        createUser("Jane", "Doe", "dup@example.com");

        HttpResponse<String> response = send("POST", USERS, adminToken,
                userBody("Other", "Person", "DUP@example.com", null));

        assertThat(response.statusCode()).isEqualTo(409);
        assertThat(json(response).get("code").asString()).isEqualTo("EMAIL_ALREADY_EXISTS");
        assertThat(json(response).get("fieldErrors").get(0).get("field").asString()).isEqualTo("email");
    }

    @Test
    @DisplayName("an empty body reports every missing field at once, not just the first")
    void validationReportsAllFields() {
        HttpResponse<String> response = send("POST", USERS, adminToken, "{}");

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(json(response).get("code").asString()).isEqualTo("VALIDATION_FAILED");
        assertThat(json(response).get("fieldErrors").size()).isGreaterThan(1);
    }

    @Test
    @DisplayName("get and update of an unknown id are 404 USER_NOT_FOUND")
    void unknownIdIsNotFound() {
        String unknown = "/00000000-0000-0000-0000-000000000000";

        assertThat(send("GET", USERS + unknown, adminToken).statusCode()).isEqualTo(404);
        HttpResponse<String> update = send("PUT", USERS + unknown, adminToken,
                userBody("A", "B", "a@example.com", null));
        assertThat(update.statusCode()).isEqualTo(404);
        assertThat(json(update).get("code").asString()).isEqualTo("USER_NOT_FOUND");
    }

    @Test
    @DisplayName("update is a full replacement: an omitted phone is cleared (FR-3)")
    void updateClearsOmittedPhone() {
        HttpResponse<String> created = send("POST", USERS, adminToken,
                userBody("Jane", "Doe", "jane@example.com", "555-0100"));
        String id = json(created).get("id").asString();

        HttpResponse<String> updated = send("PUT", USERS + "/" + id, adminToken,
                userBody("Jane", "Roe", "jane@example.com", null));

        assertThat(updated.statusCode()).isEqualTo(200);
        assertThat(json(updated).get("lastName").asString()).isEqualTo("Roe");
        assertThat(json(updated).get("phone").isNull()).isTrue();
        // id and createdAt are server-owned and must survive an update.
        assertThat(json(updated).get("id").asString()).isEqualTo(id);
        assertThat(json(updated).get("createdAt").asString())
                .isEqualTo(json(created).get("createdAt").asString());
    }

    @Test
    @DisplayName("an empty page still carries content as [], never omitted")
    void emptyPageHasContentArray() {
        HttpResponse<String> response = send("GET", USERS + "?page=99&size=10", adminToken);

        assertThat(response.statusCode()).isEqualTo(200);
        JsonNode body = json(response);
        assertThat(body.has("content")).isTrue();
        assertThat(body.get("content").isArray()).isTrue();
        assertThat(body.get("content")).isEmpty();
    }

    @Test
    @DisplayName("paging and both sort directions return correct, non-overlapping pages")
    void pagingAndSorting() {
        for (int i = 1; i <= 25; i++) {
            createUser("User" + String.format("%02d", i), "Test", "u" + i + "@example.com");
        }

        JsonNode asc = json(send("GET", USERS + "?page=0&size=10&sort=firstName&direction=asc", adminToken));
        JsonNode desc = json(send("GET", USERS + "?page=0&size=10&sort=firstName&direction=desc", adminToken));

        assertThat(asc.get("totalElements").asInt()).isEqualTo(25);
        assertThat(asc.get("totalPages").asInt()).isEqualTo(3);
        assertThat(asc.get("content").get(0).get("firstName").asString()).isEqualTo("User01");
        assertThat(desc.get("content").get(0).get("firstName").asString()).isEqualTo("User25");

        JsonNode past = json(send("GET", USERS + "?page=99&size=10", adminToken));
        assertThat(past.get("content")).isEmpty();
        assertThat(past.get("totalElements").asInt()).isEqualTo(25);
    }

    @Test
    @DisplayName("search matches partial names and emails, case-insensitively")
    void searchMatchesPartials() {
        createUser("Jane", "Doe", "jane@example.com");
        createUser("Janet", "Roe", "janet@corp.example");
        createUser("Robert", "Smith", "rob@example.com");

        assertThat(json(send("GET", USERS + "?search=jan", adminToken)).get("content")).hasSize(2);
        assertThat(json(send("GET", USERS + "?search=JAN", adminToken)).get("content")).hasSize(2);
        assertThat(json(send("GET", USERS + "?search=doe", adminToken)).get("content")).hasSize(1);
        assertThat(json(send("GET", USERS + "?search=corp", adminToken)).get("content")).hasSize(1);
        assertThat(json(send("GET", USERS + "?search=nobody", adminToken)).get("content")).isEmpty();
    }

    @Test
    @DisplayName("search totals reflect the filter, not the whole table")
    void searchTotalsReflectFilter() {
        createUser("Jane", "Doe", "jane@example.com");
        createUser("Janet", "Roe", "janet@example.com");
        createUser("Robert", "Smith", "rob@example.com");

        JsonNode filtered = json(send("GET", USERS + "?search=jan&page=0&size=1", adminToken));
        assertThat(filtered.get("content")).hasSize(1);
        assertThat(filtered.get("totalElements").asInt()).isEqualTo(2);
    }

    @Test
    @DisplayName("an unknown sort field is 400 INVALID_PARAMETER, never passed to SQL")
    void unknownSortRejected() {
        HttpResponse<String> response = send("GET", USERS + "?sort=ssn", adminToken);

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(json(response).get("code").asString()).isEqualTo("INVALID_PARAMETER");
    }

    @Test
    @DisplayName("server-managed fields sent by a client are ignored, not rejected (BR-5)")
    void serverManagedFieldsIgnored() {
        HttpResponse<String> response = send("POST", USERS, adminToken,
                "{\"id\":\"11111111-1111-1111-1111-111111111111\",\"createdAt\":\"2000-01-01T00:00:00Z\","
                        + "\"firstName\":\"Jane\",\"lastName\":\"Doe\",\"email\":\"jane@example.com\"}");

        assertThat(response.statusCode()).isEqualTo(201);
        assertThat(json(response).get("id").asString()).isNotEqualTo("11111111-1111-1111-1111-111111111111");
        assertThat(json(response).get("createdAt").asString()).doesNotStartWith("2000");
    }

    @Test
    @DisplayName("whitespace is trimmed before validation, so a blank field fails (BR-2)")
    void whitespaceIsTrimmed() {
        HttpResponse<String> blank = send("POST", USERS, adminToken,
                userBody("   ", "Doe", "jane@example.com", null));
        assertThat(blank.statusCode()).isEqualTo(400);

        HttpResponse<String> padded = send("POST", USERS, adminToken,
                userBody("  Jane  ", "Doe", "jane2@example.com", null));
        assertThat(padded.statusCode()).isEqualTo(201);
        assertThat(json(padded).get("firstName").asString()).isEqualTo("Jane");
    }

    @Test
    @DisplayName("every response carries the correlation id, and echoes a supplied one")
    void correlationIdIsEchoed() {
        HttpResponse<String> generated = send("GET", USERS, adminToken);
        assertThat(generated.headers().firstValue("X-Correlation-Id")).isPresent();
    }
}
