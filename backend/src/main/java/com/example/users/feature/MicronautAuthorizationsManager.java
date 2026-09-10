package com.example.users.feature;

import io.micronaut.http.context.ServerRequestContext;
import io.micronaut.security.utils.SecurityService;
import jakarta.inject.Singleton;
import org.ff4j.security.AuthorizationsManager;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * The Micronaut counterpart of the Spring service's authorization bridge: it
 * implements the same ff4j SPI so the identical FF4J_ROLES rows are evaluated
 * against whoever is logged in here.
 */
@Singleton
public class MicronautAuthorizationsManager implements AuthorizationsManager {

    static final String ANONYMOUS = "anonymous";

    private final SecurityService securityService;

    public MicronautAuthorizationsManager(SecurityService securityService) {
        this.securityService = securityService;
    }

    @Override
    public String getCurrentUserName() {
        return securityService.username().orElse(ANONYMOUS);
    }

    @Override
    public Set<String> getCurrentUserPermissions() {
        return securityService.getAuthentication()
                .map(auth -> {
                    Set<String> roles = new LinkedHashSet<>();
                    for (String role : auth.getRoles()) {
                        if (role.startsWith("ROLE_")) {
                            roles.add(role);
                        }
                    }
                    return roles;
                })
                .orElseGet(Set::of);
    }

    @Override
    public Set<String> listAllPermissions() {
        return Set.of("ROLE_ADMIN", "ROLE_VIEWER", "ROLE_NONE");
    }

    @Override
    public String toJson() {
        return "{\"classType\":\"" + getClass().getName() + "\"}";
    }
}
