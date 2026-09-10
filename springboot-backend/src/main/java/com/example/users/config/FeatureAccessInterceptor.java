package com.example.users.config;

import com.example.users.exception.AccessDeniedForFeatureException;
import com.example.users.feature.FeatureAccess;
import com.example.users.feature.FeatureAccessService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Set;

/**
 * Enforces the ff4j ACL on the user-data endpoints. Authentication has already
 * happened by this point; this decides authorization from FF4J_ROLES and the
 * feature's custom properties, so access rules live in the database rather than
 * in annotations that need a redeploy to change.
 */
@Component
public class FeatureAccessInterceptor implements HandlerInterceptor {

    private static final Set<String> WRITE_METHODS = Set.of("POST", "PUT", "PATCH", "DELETE");

    private final FeatureAccessService access;

    public FeatureAccessInterceptor(FeatureAccessService access) {
        this.access = access;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String feature = FeatureAccess.USER_DATA_ACCESS;

        if (!access.isAllowed(feature)) {
            throw new AccessDeniedForFeatureException(feature,
                    access.deniedMessage(feature, "Your role does not grant access to user data."));
        }

        if (WRITE_METHODS.contains(request.getMethod()) && access.isReadOnly(feature)) {
            throw new AccessDeniedForFeatureException(feature,
                    "Your role grants read-only access to user data.");
        }
        return true;
    }
}
