package com.example.users.repository;

import com.example.users.entity.UserEntity;
import io.micronaut.data.annotation.Query;
import io.micronaut.data.annotation.Repository;
import io.micronaut.data.model.Page;
import io.micronaut.data.model.Pageable;
import io.micronaut.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserRepository extends JpaRepository<UserEntity, UUID> {

    Optional<UserEntity> findByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCase(String email);

    /**
     * Free-text search across the three visible text fields (FR-5).
     *
     * <p>The leading wildcard means this cannot use a B-tree index and degrades
     * to a sequential scan. That is accepted at the scale in NFR-1; if the
     * benchmark misses 300 ms at p95, swap in the pg_trgm GIN index reserved in
     * the technical spec — a migration-only change with no API impact.
     */
    @Query(value = """
            SELECT userEntity_ FROM UserEntity userEntity_
            WHERE LOWER(userEntity_.firstName) LIKE :pattern
               OR LOWER(userEntity_.lastName) LIKE :pattern
               OR LOWER(userEntity_.email) LIKE :pattern
            """,
            countQuery = """
            SELECT COUNT(userEntity_) FROM UserEntity userEntity_
            WHERE LOWER(userEntity_.firstName) LIKE :pattern
               OR LOWER(userEntity_.lastName) LIKE :pattern
               OR LOWER(userEntity_.email) LIKE :pattern
            """)
    Page<UserEntity> search(String pattern, Pageable pageable);
}
