package com.example.users.feature;

import org.ff4j.security.AuthorizationsManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Bridges the logged-in Spring Security principal into ff4j, so a feature's
 * FF4J_ROLES rows are evaluated against the caller's actual roles.
 *
 * <p>The Micronaut service has its own implementation of this same ff4j interface
 * reading the same rows — neither service depends on the other to decide access.
 */
@Component
public class SpringAuthorizationsManager implements AuthorizationsManager {

    static final String ANONYMOUS = "anonymous";

    @Override
    public String getCurrentUserName() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication == null ? ANONYMOUS : authentication.getName();
    }

    @Override
    public Set<String> getCurrentUserPermissions() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return Set.of();
        }
        Set<String> roles = new LinkedHashSet<>();
        for (GrantedAuthority authority : authentication.getAuthorities()) {
            // Only ROLE_*: Spring Security 7 also grants authentication-factor
            // authorities, which are not roles and must not satisfy an ACL.
            if (authority.getAuthority().startsWith("ROLE_")) {
                roles.add(authority.getAuthority());
            }
        }
        return roles;
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
