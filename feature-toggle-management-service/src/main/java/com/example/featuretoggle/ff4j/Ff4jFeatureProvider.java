package com.example.featuretoggle.ff4j;

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
 * Bridges OpenFeature to ff4j. Application code depends only on the vendor-neutral
 * OpenFeature API, so swapping ff4j for another backend changes this one class.
 */
public class Ff4jFeatureProvider implements FeatureProvider {

    private static final Logger LOG = LoggerFactory.getLogger(Ff4jFeatureProvider.class);

    private final FF4j ff4j;

    public Ff4jFeatureProvider(FF4j ff4j) {
        this.ff4j = ff4j;
    }

    @Override
    public Metadata getMetadata() {
        return () -> "ff4j";
    }

    @Override
    public ProviderEvaluation<Boolean> getBooleanEvaluation(String key, Boolean defaultValue, EvaluationContext ctx) {
        try {
            if (!ff4j.getFeatureStore().exist(key)) {
                return ProviderEvaluation.<Boolean>builder()
                        .value(defaultValue).reason(Reason.DEFAULT.name()).build();
            }
            return ProviderEvaluation.<Boolean>builder()
                    .value(ff4j.check(key)).reason(Reason.TARGETING_MATCH.name()).build();
        } catch (RuntimeException ex) {
            LOG.warn("ff4j evaluation of '{}' failed, falling back to {}", key, defaultValue, ex);
            return ProviderEvaluation.<Boolean>builder()
                    .value(defaultValue).reason(Reason.ERROR.name()).errorMessage(ex.getMessage()).build();
        }
    }

    @Override
    public ProviderEvaluation<String> getStringEvaluation(String k, String d, EvaluationContext c) { return def(d); }

    @Override
    public ProviderEvaluation<Integer> getIntegerEvaluation(String k, Integer d, EvaluationContext c) { return def(d); }

    @Override
    public ProviderEvaluation<Double> getDoubleEvaluation(String k, Double d, EvaluationContext c) { return def(d); }

    @Override
    public ProviderEvaluation<Value> getObjectEvaluation(String k, Value d, EvaluationContext c) { return def(d); }

    private static <T> ProviderEvaluation<T> def(T defaultValue) {
        return ProviderEvaluation.<T>builder().value(defaultValue).reason(Reason.DEFAULT.name()).build();
    }
}
