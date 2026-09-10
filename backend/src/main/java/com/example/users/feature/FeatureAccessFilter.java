package com.example.users.feature;

import com.example.users.exception.AccessDeniedForFeatureException;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.annotation.RequestFilter;
import io.micronaut.http.annotation.ServerFilter;

import java.util.Set;

/**
 * Enforces the ff4j ACL on the user-data endpoints, independently of the Spring
 * service. Access rules live in the database, so changing who may read or write
 * is a row update rather than a redeploy of either backend.
 */
// Both patterns: "/**" does not match the collection path itself.
@ServerFilter({"/api/v1/users", "/api/v1/users/**"})
public class FeatureAccessFilter {

    private static final Set<String> WRITE_METHODS = Set.of("POST", "PUT", "PATCH", "DELETE");

    private final FeatureAccessService access;

    public FeatureAccessFilter(FeatureAccessService access) {
        this.access = access;
    }

    @RequestFilter
    public void onRequest(HttpRequest<?> request) {
        String feature = FeatureAccessService.USER_DATA_ACCESS;

        if (!access.isAllowed(feature)) {
            throw new AccessDeniedForFeatureException(feature,
                    access.deniedMessage(feature, "Your role does not grant access to user data."));
        }
        if (WRITE_METHODS.contains(request.getMethodName()) && access.isReadOnly(feature)) {
            throw new AccessDeniedForFeatureException(feature,
                    "Your role grants read-only access to user data.");
        }
    }
}
