package com.example.users.repository;

import com.example.users.entity.AuthUserEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface AuthUserRepository extends JpaRepository<AuthUserEntity, UUID> {
    Optional<AuthUserEntity> findByUsernameIgnoreCase(String username);
}
