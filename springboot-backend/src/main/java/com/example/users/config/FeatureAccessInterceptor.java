package com.example.users.config;

import com.example.users.exception.AccessDeniedForFeatureException;
import com.example.users.gate.FeatureDecision;
import com.example.users.gate.FeatureGateClient;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Set;

/**
 * Enforces the user-data ACL on this service's endpoints, using the decision the
 * feature service returns. No rules live here: this asks, and applies the answer.
 */
@Component
public class FeatureAccessInterceptor implements HandlerInterceptor {

    /** Kept in step with the feature service's catalog, by name only. */
    static final String USER_DATA_ACCESS = "user-data-access";

    private static final Set<String> WRITE_METHODS = Set.of("POST", "PUT", "PATCH", "DELETE");

    private final FeatureGateClient gate;

    public FeatureAccessInterceptor(FeatureGateClient gate) {
        this.gate = gate;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String token = bearerToken(request);
        if (token == null) {
            // Unauthenticated requests never reach here; the security filter chain
            // has already refused them.
            return true;
        }

        FeatureDecision decision = gate.decide(USER_DATA_ACCESS, token);

        if (!decision.allowed()) {
            throw new AccessDeniedForFeatureException(USER_DATA_ACCESS,
                    decision.deniedMessage() == null
                            ? "Your role does not grant access to user data."
                            : decision.deniedMessage());
        }
        if (decision.readOnly() && WRITE_METHODS.contains(request.getMethod())) {
            throw new AccessDeniedForFeatureException(USER_DATA_ACCESS,
                    "Your role grants read-only access to user data.");
        }
        return true;
    }

    private static String bearerToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        return header != null && header.startsWith("Bearer ") ? header.substring(7) : null;
    }
}
