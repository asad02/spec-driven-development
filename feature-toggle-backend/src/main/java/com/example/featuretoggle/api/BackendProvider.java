package com.example.featuretoggle.api;

/** The two product backends the routing flag chooses between. */
public enum BackendProvider {

    MICRONAUT("micronaut", "backend"),
    SPRINGBOOT("springboot", "springboot-backend");

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
