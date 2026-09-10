package com.example.users.feature;

import dev.openfeature.sdk.EvaluationContext;
import dev.openfeature.sdk.FeatureProvider;
import dev.openfeature.sdk.Metadata;
import dev.openfeature.sdk.ProviderEvaluation;
import dev.openfeature.sdk.Reason;
import dev.openfeature.sdk.Value;
import org.ff4j.FF4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Bridges OpenFeature to ff4j: application code depends only on the vendor-neutral
 * OpenFeature API, and this one class is all that would change if ff4j were
 * swapped for another flag backend.
 *
 * <p>Only boolean evaluation is backed by ff4j features; the other types resolve
 * to their defaults rather than pretending to support something ff4j's feature
 * store does not model here.
 */
public class Ff4jFeatureProvider implements FeatureProvider {

    private static final Logger LOG = LoggerFactory.getLogger(Ff4jFeatureProvider.class);
    private static final String NAME = "ff4j";

    private final FF4j ff4j;

    public Ff4jFeatureProvider(FF4j ff4j) {
        this.ff4j = ff4j;
    }

    @Override
    public Metadata getMetadata() {
        return () -> NAME;
    }

    @Override
    public ProviderEvaluation<Boolean> getBooleanEvaluation(String key, Boolean defaultValue, EvaluationContext ctx) {
        try {
            if (!ff4j.exist(key)) {
                return ProviderEvaluation.<Boolean>builder()
                        .value(defaultValue)
                        .reason(Reason.DEFAULT.name())
                        .build();
            }
            return ProviderEvaluation.<Boolean>builder()
                    .value(ff4j.check(key))
                    .reason(Reason.TARGETING_MATCH.name())
                    .build();
        } catch (RuntimeException ex) {
            // A flag store outage must never take the API down with it.
            LOG.warn("ff4j evaluation of '{}' failed, falling back to default {}", key, defaultValue, ex);
            return ProviderEvaluation.<Boolean>builder()
                    .value(defaultValue)
                    .reason(Reason.ERROR.name())
                    .errorMessage(ex.getMessage())
                    .build();
        }
    }

    @Override
    public ProviderEvaluation<String> getStringEvaluation(String key, String defaultValue, EvaluationContext ctx) {
        return unsupported(defaultValue);
    }

    @Override
    public ProviderEvaluation<Integer> getIntegerEvaluation(String key, Integer defaultValue, EvaluationContext ctx) {
        return unsupported(defaultValue);
    }

    @Override
    public ProviderEvaluation<Double> getDoubleEvaluation(String key, Double defaultValue, EvaluationContext ctx) {
        return unsupported(defaultValue);
    }

    @Override
    public ProviderEvaluation<Value> getObjectEvaluation(String key, Value defaultValue, EvaluationContext ctx) {
        return unsupported(defaultValue);
    }

    private static <T> ProviderEvaluation<T> unsupported(T defaultValue) {
        return ProviderEvaluation.<T>builder()
                .value(defaultValue)
                .reason(Reason.DEFAULT.name())
                .build();
    }
}
