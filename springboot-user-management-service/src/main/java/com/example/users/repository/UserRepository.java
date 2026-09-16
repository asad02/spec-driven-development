package com.example.users.repository;

import com.example.users.entity.UserEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<UserEntity, UUID> {

    Optional<UserEntity> findByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCase(String email);

    /**
     * Free-text search across the three visible text fields (FR-5).
     *
     * <p>The alias is plain {@code u}: Spring Data derives the alias for the
     * {@code ORDER BY} it appends from the query itself, so no contrived alias is
     * needed here (technical-spec-springboot §8.3).
     *
     * <p>The leading wildcard means this cannot use a B-tree index and degrades to
     * a sequential scan. Accepted at NFR-1's scale; swap in the pg_trgm GIN index
     * if the benchmark misses 300 ms at p95.
     */
    @Query(value = """
            SELECT u FROM UserEntity u
            WHERE LOWER(u.firstName) LIKE :pattern
               OR LOWER(u.lastName) LIKE :pattern
               OR LOWER(u.email) LIKE :pattern
            """,
            countQuery = """
            SELECT COUNT(u) FROM UserEntity u
            WHERE LOWER(u.firstName) LIKE :pattern
               OR LOWER(u.lastName) LIKE :pattern
               OR LOWER(u.email) LIKE :pattern
            """)
    Page<UserEntity> search(@Param("pattern") String pattern, Pageable pageable);
}
