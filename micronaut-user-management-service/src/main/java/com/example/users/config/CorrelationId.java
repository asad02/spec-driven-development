package com.example.users.config;

import io.micronaut.http.HttpRequest;

public final class CorrelationId {

    public static final String HEADER = "X-Correlation-Id";
    public static final String MDC_KEY = "correlationId";
    public static final String ATTRIBUTE = "correlationId";

    private CorrelationId() {
    }

    /** Reads the id a filter attached to this request, for echoing in an error body. */
    public static String of(HttpRequest<?> request) {
        if (request == null) {
            return null;
        }
        return request.getAttribute(ATTRIBUTE, String.class).orElse(null);
    }
}
