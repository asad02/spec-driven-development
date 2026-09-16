package com.example.featuretoggle.exception;

public class FeatureAlreadyExistsException extends RuntimeException {
    public FeatureAlreadyExistsException(String uid) {
        super("A feature with id " + uid + " already exists.");
    }
}
