package com.example.users.feature;

/**
 * The feature UIDs and custom-property keys that carry access policy.
 *
 * <p>Two tables, two distinct jobs:
 * <ul>
 *   <li>{@code FF4J_ROLES} — ff4j's native ACL. Which roles may use a feature at
 *       all, enforced through {@code FF4j.check()} and the AuthorizationsManager.</li>
 *   <li>{@code FF4J_CUSTOM_PROPERTIES} — policy the ACL cannot express: who may
 *       administer a flag, which roles are read-only, and the message to return
 *       when access is refused.</li>
 * </ul>
 */
public final class FeatureAccess {

    /** Gates /api/v1/users. Its FF4J_ROLES rows are the allow-list. */
    public static final String USER_DATA_ACCESS = "user-data-access";

    /**
     * The routing flag. Deliberately carries NO FF4J_ROLES rows: nginx evaluates it
     * through an anonymous auth_request subrequest, and a role-restricted feature
     * would evaluate false for that caller and collapse routing to one backend for
     * everybody. Who may *flip* it comes from the ADMIN_ROLE custom property instead.
     */
    public static final String BACKEND_ROUTING = "use-springboot-backend";

    /** Custom-property keys. */
    public static final String PROP_ADMIN_ROLE = "adminRole";
    public static final String PROP_READ_ONLY_ROLES = "readOnlyRoles";
    public static final String PROP_DENIED_MESSAGE = "deniedMessage";

    private FeatureAccess() {
    }
}
