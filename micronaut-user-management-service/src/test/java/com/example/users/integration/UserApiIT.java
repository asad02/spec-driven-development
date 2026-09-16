package com.example.users.integration;

import com.example.users.dto.ApiError;
import com.example.users.dto.PageResponse;
import com.example.users.dto.UserRequest;
import com.example.users.dto.UserResponse;
import com.example.users.repository.UserRepository;
import io.micronaut.core.type.Argument;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.MutableHttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.security.authentication.UsernamePasswordCredentials;
import io.micronaut.security.token.render.BearerAccessRefreshToken;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

@MicronautTest(environments = "test", transactional = false)
class UserApiIT extends AbstractIntegrationTest {

    @Inject
    @Client("/")
    HttpClient http;

    @Inject
    UserRepository userRepository;

    private String token;

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();
        token = login("admin", "admin123!");
    }

    private String login(String username, String password) {
        BearerAccessRefreshToken body = http.toBlocking().retrieve(
                HttpRequest.POST("/api/v1/auth/login", new UsernamePasswordCredentials(username, password)),
                BearerAccessRefreshToken.class);
        return body.getAccessToken();
    }

    private <T> HttpResponse<T> send(MutableHttpRequest<?> request, Class<T> type) {
        return http.toBlocking().exchange(request.bearerAuth(token), Argument.of(type), Argument.of(ApiError.class));
    }

    private UserResponse createUser(String first, String last, String email, String phone) {
        return send(HttpRequest.POST("/api/v1/users", new UserRequest(first, last, email, phone)),
                UserResponse.class).body();
    }

    @Test
    @DisplayName("create returns 201, a Location header and the stored record")
    void createReturns201() {
        HttpResponse<UserResponse> response = send(
                HttpRequest.POST("/api/v1/users", new UserRequest("Jane", "Doe", "jane@example.com", "+1 555 0100")),
                UserResponse.class);

        assertThat(response.status().getCode()).isEqualTo(201);
        UserResponse body = response.body();
        assertThat(body.id()).isNotNull();
        assertThat(body.firstName()).isEqualTo("Jane");
        assertThat(body.email()).isEqualTo("jane@example.com");
        assertThat(body.createdAt()).isNotNull();
        assertThat(body.updatedAt()).isEqualTo(body.createdAt());
        assertThat(response.header("Location")).isEqualTo("/api/v1/users/" + body.id());
    }

    @Test
    @DisplayName("acceptance 05: an empty body reports every missing field at once")
    void validationReportsAllFields() {
        HttpClientResponseException ex = catchThrowableOfType(
                () -> send(HttpRequest.POST("/api/v1/users", new UserRequest(null, null, null, null)),
                        UserResponse.class),
                HttpClientResponseException.class);

        assertThat(ex.getStatus().getCode()).isEqualTo(400);
        ApiError error = ex.getResponse().getBody(ApiError.class).orElseThrow();
        assertThat(error.code()).isEqualTo("VALIDATION_FAILED");
        assertThat(error.correlationId()).isNotBlank();
        assertThat(error.fieldErrors()).extracting(ApiError.FieldError::field)
                .contains("firstName", "lastName", "email");
    }

    @Test
    @DisplayName("acceptance 04: a duplicate email is a 409 that names the email field")
    void duplicateEmailIsConflict() {
        createUser("Jane", "Doe", "jane@example.com", null);

        HttpClientResponseException ex = catchThrowableOfType(
                () -> createUser("Other", "Person", "JANE@example.com", null),
                HttpClientResponseException.class);

        assertThat(ex.getStatus().getCode()).isEqualTo(409);
        ApiError error = ex.getResponse().getBody(ApiError.class).orElseThrow();
        assertThat(error.code()).isEqualTo("EMAIL_ALREADY_EXISTS");
        assertThat(error.fieldErrors()).extracting(ApiError.FieldError::field).containsExactly("email");
        // The rejected create must not have left a partial record behind.
        assertThat(userRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("acceptance 06: update changes only the submitted fields and advances updatedAt")
    void updateReplacesEditableFields() throws InterruptedException {
        UserResponse created = createUser("Jane", "Doe", "jane@example.com", "+1 555 0100");
        Thread.sleep(10);

        UserResponse updated = send(
                HttpRequest.PUT("/api/v1/users/" + created.id(),
                        new UserRequest("Janet", "Roe", "janet@example.com", null)),
                UserResponse.class).body();

        assertThat(updated.id()).isEqualTo(created.id());
        assertThat(updated.createdAt()).isEqualTo(created.createdAt());
        assertThat(updated.updatedAt()).isAfter(created.updatedAt());
        assertThat(updated.firstName()).isEqualTo("Janet");
        assertThat(updated.phone()).isNull();
    }

    @Test
    @DisplayName("get and update of an unknown id are 404 USER_NOT_FOUND")
    void unknownIdIsNotFound() {
        UUID unknown = UUID.randomUUID();

        HttpClientResponseException get = catchThrowableOfType(
                () -> send(HttpRequest.GET("/api/v1/users/" + unknown), UserResponse.class),
                HttpClientResponseException.class);
        assertThat(get.getStatus().getCode()).isEqualTo(404);
        assertThat(get.getResponse().getBody(ApiError.class).orElseThrow().code()).isEqualTo("USER_NOT_FOUND");

        HttpClientResponseException put = catchThrowableOfType(
                () -> send(HttpRequest.PUT("/api/v1/users/" + unknown,
                        new UserRequest("A", "B", "a@b.com", null)), UserResponse.class),
                HttpClientResponseException.class);
        assertThat(put.getStatus().getCode()).isEqualTo(404);
    }

    @Test
    @DisplayName("acceptance 10: DELETE is not allowed")
    void deleteIsMethodNotAllowed() {
        UserResponse created = createUser("Jane", "Doe", "jane@example.com", null);

        HttpClientResponseException ex = catchThrowableOfType(
                () -> send(HttpRequest.DELETE("/api/v1/users/" + created.id()), Object.class),
                HttpClientResponseException.class);

        assertThat(ex.getStatus().getCode()).isEqualTo(405);
        assertThat(userRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("acceptance 07: paging and both sort directions return correct, non-overlapping pages")
    void pagingAndSorting() {
        for (int i = 1; i <= 25; i++) {
            createUser("User" + String.format("%02d", i), "Test", "user" + String.format("%02d", i) + "@example.com", null);
        }

        PageResponse<UserResponse> first = listUsers("?page=0&size=10&sort=firstName&direction=asc");
        assertThat(first.content()).hasSize(10);
        assertThat(first.totalElements()).isEqualTo(25);
        assertThat(first.totalPages()).isEqualTo(3);
        assertThat(first.content().get(0).firstName()).isEqualTo("User01");

        PageResponse<UserResponse> last = listUsers("?page=2&size=10&sort=firstName&direction=asc");
        assertThat(last.content()).hasSize(5);
        assertThat(last.content().get(0).firstName()).isEqualTo("User21");

        List<String> ascNames = first.content().stream().map(UserResponse::firstName).toList();
        List<String> descNames = listUsers("?page=0&size=10&sort=firstName&direction=desc")
                .content().stream().map(UserResponse::firstName).toList();
        assertThat(ascNames).doesNotContainAnyElementsOf(descNames);
        assertThat(descNames.get(0)).isEqualTo("User25");

        // A page beyond the end is empty, not an error (FR-5).
        PageResponse<UserResponse> past = listUsers("?page=99&size=10");
        assertThat(past.content()).isEmpty();
        assertThat(past.totalElements()).isEqualTo(25);
    }

    @Test
    @DisplayName("acceptance 08: search matches partial names and emails, case-insensitively")
    void searchMatchesPartialsCaseInsensitively() {
        createUser("Jane", "Doe", "jane@example.com", null);
        createUser("Janet", "Roe", "janet@corp.example", null);
        createUser("Robert", "Smith", "rob@example.com", null);

        assertThat(listUsers("?search=jan").content()).hasSize(2);
        assertThat(listUsers("?search=JAN").content()).hasSize(2);
        assertThat(listUsers("?search=doe").content()).hasSize(1);
        assertThat(listUsers("?search=corp").content()).hasSize(1);

        PageResponse<UserResponse> filtered = listUsers("?search=jan&page=0&size=1");
        assertThat(filtered.content()).hasSize(1);
        // Totals reflect the filtered set, not the whole table.
        assertThat(filtered.totalElements()).isEqualTo(2);

        assertThat(listUsers("?search=nobody").content()).isEmpty();
    }

    @Test
    @DisplayName("an unknown sort field is a 400 INVALID_PARAMETER, never passed to SQL")
    void unknownSortRejected() {
        HttpClientResponseException ex = catchThrowableOfType(
                () -> listUsers("?sort=password_hash"), HttpClientResponseException.class);

        assertThat(ex.getStatus().getCode()).isEqualTo(400);
        assertThat(ex.getResponse().getBody(ApiError.class).orElseThrow().code()).isEqualTo("INVALID_PARAMETER");
    }

    @Test
    @DisplayName("BR-2: surrounding whitespace is trimmed before storage")
    void trimsWhitespace() {
        UserResponse created = createUser("  Jane  ", "  Doe  ", "  jane@example.com  ", null);

        assertThat(created.firstName()).isEqualTo("Jane");
        assertThat(created.lastName()).isEqualTo("Doe");
        assertThat(created.email()).isEqualTo("jane@example.com");
    }

    @Test
    @DisplayName("a whitespace-only required field fails validation rather than being stored")
    void whitespaceOnlyFailsValidation() {
        HttpClientResponseException ex = catchThrowableOfType(
                () -> createUser("   ", "Doe", "jane@example.com", null),
                HttpClientResponseException.class);

        assertThat(ex.getStatus().getCode()).isEqualTo(400);
        assertThat(ex.getResponse().getBody(ApiError.class).orElseThrow().fieldErrors())
                .extracting(ApiError.FieldError::field).contains("firstName");
    }

    @Test
    @DisplayName("BR-5: server-managed fields sent by a client are ignored, not rejected")
    void ignoresServerManagedFields() {
        String payload = """
                {"id":"11111111-1111-1111-1111-111111111111",
                 "firstName":"Jane","lastName":"Doe","email":"jane@example.com",
                 "createdAt":"1999-01-01T00:00:00Z"}
                """;
        UserResponse created = send(
                HttpRequest.POST("/api/v1/users", payload).contentType("application/json"),
                UserResponse.class).body();

        assertThat(created.id()).isNotEqualTo(UUID.fromString("11111111-1111-1111-1111-111111111111"));
        assertThat(created.createdAt()).isAfter(java.time.Instant.parse("2020-01-01T00:00:00Z"));
    }

    @Test
    @DisplayName("NFR-6: every response carries the correlation id, and echoes a supplied one")
    void correlationIdIsEchoed() {
        HttpResponse<UserResponse> generated = send(
                HttpRequest.POST("/api/v1/users", new UserRequest("Jane", "Doe", "jane@example.com", null)),
                UserResponse.class);
        assertThat(generated.header("X-Correlation-Id")).isNotBlank();

        String supplied = "my-trace-id-123";
        HttpResponse<PageResponse> echoed = http.toBlocking().exchange(
                HttpRequest.GET("/api/v1/users").bearerAuth(token).header("X-Correlation-Id", supplied),
                Argument.of(PageResponse.class), Argument.of(ApiError.class));
        assertThat(echoed.header("X-Correlation-Id")).isEqualTo(supplied);
    }

    @SuppressWarnings("unchecked")
    private PageResponse<UserResponse> listUsers(String query) {
        return http.toBlocking().exchange(
                HttpRequest.GET("/api/v1/users" + query).bearerAuth(token),
                Argument.of(PageResponse.class, UserResponse.class),
                Argument.of(ApiError.class)).body();
    }
}
