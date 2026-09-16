package com.example.users.gate;

import com.example.users.exception.AccessDeniedForFeatureException;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.annotation.RequestFilter;
import io.micronaut.http.annotation.ServerFilter;

import java.util.Set;

/**
 * Enforces the user-data ACL using the decision the feature service returns. No
 * rules live here: this asks, and applies the answer.
 */
// Both patterns: "/**" does not match the collection path itself.
@ServerFilter({"/api/v1/users", "/api/v1/users/**"})
public class FeatureAccessFilter {

    /** Kept in step with the feature service's catalog, by name only. */
    static final String USER_DATA_ACCESS = "user-data-access";

    private static final Set<String> WRITE_METHODS = Set.of("POST", "PUT", "PATCH", "DELETE");

    private final FeatureGateClient gate;

    public FeatureAccessFilter(FeatureGateClient gate) {
        this.gate = gate;
    }

    @RequestFilter
    public void onRequest(HttpRequest<?> request) {
        String token = request.getHeaders().getAuthorization()
                .filter(h -> h.startsWith("Bearer "))
                .map(h -> h.substring(7))
                .orElse(null);
        if (token == null) {
            // Unauthenticated requests never reach here; security already refused them.
            return;
        }

        FeatureDecision decision = gate.decide(USER_DATA_ACCESS, token);

        if (!decision.allowed()) {
            throw new AccessDeniedForFeatureException(USER_DATA_ACCESS,
                    decision.deniedMessage() == null
                            ? "Your role does not grant access to user data."
                            : decision.deniedMessage());
        }
        if (decision.readOnly() && WRITE_METHODS.contains(request.getMethodName())) {
            throw new AccessDeniedForFeatureException(USER_DATA_ACCESS,
                    "Your role grants read-only access to user data.");
        }
    }
}
