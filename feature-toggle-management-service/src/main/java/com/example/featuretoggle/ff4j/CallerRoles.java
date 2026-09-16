package com.example.featuretoggle.ff4j;

import org.ff4j.security.AuthorizationsManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Bridges the caller's JWT into ff4j so FF4J_ROLES is evaluated against their
 * actual roles.
 *
 * <p>This service issues no tokens. It validates tokens minted by the product
 * backends using the shared signing secret, and reads roles from the `roles`
 * claim — which is why every service must agree on that claim name.
 */
@Component
public class CallerRoles implements AuthorizationsManager {

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
