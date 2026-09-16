package com.example.featuretoggle.exception;

/**
 * A change that would break the system's ability to administer itself or to reach
 * user data. The message says what would have broken, not merely that it was
 * refused.
 */
public class FeatureProtectedException extends RuntimeException {

    private final String feature;

    public FeatureProtectedException(String feature, String message) {
        super(message);
        this.feature = feature;
    }

    public String getFeature() { return feature; }
}
