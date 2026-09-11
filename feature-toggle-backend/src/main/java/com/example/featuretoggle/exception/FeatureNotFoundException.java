package com.example.featuretoggle.exception;

public class FeatureNotFoundException extends RuntimeException {
    public FeatureNotFoundException(String uid) {
        super("No feature with id " + uid + ".");
    }
}
