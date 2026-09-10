package com.example.users.feature;

import dev.openfeature.sdk.Client;
import org.ff4j.FF4j;
import org.springframework.stereotype.Service;

/**
 * Reads flags through OpenFeature; writes them through ff4j. OpenFeature is
 * deliberately an evaluation API only — it has no mutation surface — so an
 * administrative flip goes to the store directly.
 */
@Service
public class FeatureService {

    private final Client featureClient;
    private final FF4j ff4j;

    public FeatureService(Client featureClient, FF4j ff4j) {
        this.featureClient = featureClient;
        this.ff4j = ff4j;
    }

    /** Which service should serve /api right now. */
    public BackendProvider activeBackend() {
        boolean useSpring = featureClient.getBooleanValue(Ff4jConfig.USE_SPRINGBOOT_BACKEND, false);
        return useSpring ? BackendProvider.SPRINGBOOT : BackendProvider.MICRONAUT;
    }

    public BackendProvider setActiveBackend(BackendProvider provider) {
        if (provider == BackendProvider.SPRINGBOOT) {
            ff4j.enable(Ff4jConfig.USE_SPRINGBOOT_BACKEND);
        } else {
            ff4j.disable(Ff4jConfig.USE_SPRINGBOOT_BACKEND);
        }
        return activeBackend();
    }
}
