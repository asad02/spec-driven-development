package com.example.users.repository;

import com.example.users.entity.AuthUserEntity;
import io.micronaut.data.annotation.Repository;
import io.micronaut.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface AuthUserRepository extends JpaRepository<AuthUserEntity, UUID> {

    Optional<AuthUserEntity> findByUsernameIgnoreCase(String username);
}
