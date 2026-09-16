package com.example.featuretoggle.ff4j;

/** Well-known feature ids and the custom-property keys that carry policy. */
public final class FeatureCatalog {

    /** Gates /api/v1/users on the product backends. Its FF4J_ROLES rows are the allow-list. */
    public static final String USER_DATA_ACCESS = "user-data-access";

    /**
     * Which product backend serves /api. Carries no FF4J_ROLES on purpose: nginx
     * evaluates it through an anonymous subrequest, and a role-restricted feature
     * would evaluate false for that caller and pin routing to one backend.
     */
    public static final String BACKEND_ROUTING = "use-springboot-backend";

    public static final String PROP_ADMIN_ROLE = "adminRole";
    public static final String PROP_READ_ONLY_ROLES = "readOnlyRoles";
    public static final String PROP_DENIED_MESSAGE = "deniedMessage";

    private FeatureCatalog() {
    }
}
