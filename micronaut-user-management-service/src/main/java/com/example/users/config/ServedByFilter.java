package com.example.users.config;

import io.micronaut.http.MutableHttpResponse;
import io.micronaut.http.annotation.ResponseFilter;
import io.micronaut.http.annotation.ServerFilter;

import static io.micronaut.http.annotation.ServerFilter.MATCH_ALL_PATTERN;

/**
 * Stamps every response with the implementation that produced it, so the UI can
 * show which backend actually answered rather than which one it believes the
 * feature flag selected. Without this, a misrouted request is indistinguishable
 * from a correct one.
 */
@ServerFilter(MATCH_ALL_PATTERN)
public class ServedByFilter {

    public static final String HEADER = "X-Served-By";

    @ResponseFilter
    public void onResponse(MutableHttpResponse<?> response) {
        response.getHeaders().add(HEADER, "micronaut");
    }
}
