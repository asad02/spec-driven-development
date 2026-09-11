package com.example.users.exception;

/** Raised when the caller's roles do not satisfy a feature's policy. */
public class AccessDeniedForFeatureException extends RuntimeException {

    private final String feature;

    public AccessDeniedForFeatureException(String feature, String message) {
        super(message);
        this.feature = feature;
    }

    public String getFeature() {
        return feature;
    }
}
