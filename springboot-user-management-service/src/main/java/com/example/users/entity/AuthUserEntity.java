package com.example.users.entity;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

/**
 * An operator login. Deliberately unrelated to {@link UserEntity} — an operator
 * is not a user record, and coupling them would force one to exist for the other.
 */
@Entity
@Table(name = "auth_user")
public class AuthUserEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "username", nullable = false, length = 100)
    private String username;

    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    @Column(name = "roles", nullable = false, length = 255)
    private String roles;

    @Column(name = "enabled", nullable = false)
    private boolean enabled = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }
    public String getRoles() { return roles; }
    public void setRoles(String roles) { this.roles = roles; }
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    /** Never let the hash reach a log line or a stack trace. */
    @Override
    public String toString() {
        return "AuthUserEntity{id=" + id + ", username='" + username + "', enabled=" + enabled + "}";
    }
}
