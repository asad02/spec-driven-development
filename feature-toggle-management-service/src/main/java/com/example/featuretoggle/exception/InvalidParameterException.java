package com.example.featuretoggle.exception;

public class InvalidParameterException extends RuntimeException {

    private final String parameter;

    public InvalidParameterException(String parameter, String message) {
        super(message);
        this.parameter = parameter;
    }

    public String getParameter() { return parameter; }
}
