package com.example.users.integration;

import com.example.users.entity.UserEntity;
import com.example.users.repository.UserRepository;
import io.micronaut.data.model.Pageable;
import io.micronaut.data.model.Sort;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Exercises the parts that only a real PostgreSQL can prove: the migrations,
 * the case-insensitive unique index, and the search query's SQL.
 */
@MicronautTest(environments = "test", transactional = false)
class UserRepositoryIT extends AbstractIntegrationTest {

    @Inject
    UserRepository userRepository;

    @BeforeEach
    void clear() {
        userRepository.deleteAll();
    }

    @Test
    @DisplayName("Flyway applied every migration from an empty database")
    void migrationsApplied() throws Exception {
        // Deliberately not the injected DataSource: Micronaut hands back a contextual
        // connection proxy that refuses use outside a @Connectable/@Transactional scope.
        // Reading flyway_schema_history is a raw-JDBC question, so go straight to the
        // container.
        try (Connection connection = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
             Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery(
                     "SELECT version, success FROM flyway_schema_history ORDER BY installed_rank")) {
            int count = 0;
            while (rs.next()) {
                assertThat(rs.getBoolean("success")).isTrue();
                count++;
            }
            // V1 users, V2 auth_user, V3 seed admin, V4 seed role-test operators.
            assertThat(count).isEqualTo(4);
        }
    }

    @Test
    @DisplayName("the unique index rejects the same email in a different case")
    void uniqueIndexIsCaseInsensitive() {
        userRepository.saveAndFlush(new UserEntity("Jane", "Doe", "jane@example.com", null));

        assertThatThrownBy(() ->
                userRepository.saveAndFlush(new UserEntity("Other", "Person", "JANE@EXAMPLE.COM", null)))
                .isInstanceOf(Exception.class);
    }

    @Test
    @DisplayName("findByEmailIgnoreCase matches regardless of case")
    void findByEmailIgnoresCase() {
        userRepository.saveAndFlush(new UserEntity("Jane", "Doe", "Jane@Example.com", null));

        assertThat(userRepository.findByEmailIgnoreCase("jane@example.com")).isPresent();
        assertThat(userRepository.existsByEmailIgnoreCase("JANE@EXAMPLE.COM")).isTrue();
    }

    @Test
    @DisplayName("the search query matches a substring in any of the three fields")
    void searchSpansAllThreeFields() {
        userRepository.saveAndFlush(new UserEntity("Jane", "Doe", "jd@example.com", null));
        userRepository.saveAndFlush(new UserEntity("Robert", "Janeway", "rj@example.com", null));
        userRepository.saveAndFlush(new UserEntity("Ann", "Smith", "jane.smith@corp.example", null));
        userRepository.saveAndFlush(new UserEntity("Nobody", "Here", "nh@example.com", null));

        Pageable pageable = Pageable.from(0, 20, Sort.of(Sort.Order.asc("firstName")));
        assertThat(userRepository.search("%jane%", pageable).getTotalSize()).isEqualTo(3);
        assertThat(userRepository.search("%smith%", pageable).getTotalSize()).isEqualTo(1);
        assertThat(userRepository.search("%zzz%", pageable).getTotalSize()).isZero();
    }

    @Test
    @DisplayName("timestamps are written by the entity callbacks, and updatedAt advances")
    void timestampsAreManaged() throws InterruptedException {
        UserEntity saved = userRepository.saveAndFlush(new UserEntity("Jane", "Doe", "jane@example.com", null));
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isEqualTo(saved.getCreatedAt());

        Thread.sleep(10);
        saved.setFirstName("Janet");
        UserEntity updated = userRepository.update(saved);
        userRepository.flush();

        assertThat(updated.getUpdatedAt()).isAfter(updated.getCreatedAt());
    }
}
