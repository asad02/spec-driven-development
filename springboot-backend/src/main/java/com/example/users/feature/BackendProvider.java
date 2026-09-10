package com.example.users.feature;

/** The two services the toggle chooses between. */
public enum BackendProvider {

    MICRONAUT("micronaut", "backend"),
    SPRINGBOOT("springboot", "springboot-backend");

    /** Name shown in the UI and returned by /api/v1/features. */
    private final String id;
    /** Docker Compose service name — what nginx proxies to. */
    private final String host;

    BackendProvider(String id, String host) {
        this.id = id;
        this.host = host;
    }

    public String id() { return id; }
    public String host() { return host; }
}
