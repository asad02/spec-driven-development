package com.example.users.config;

import io.micronaut.core.propagation.MutablePropagatedContext;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.http.annotation.RequestFilter;
import io.micronaut.http.annotation.ResponseFilter;
import io.micronaut.http.annotation.ServerFilter;

import java.util.UUID;

import static io.micronaut.http.annotation.ServerFilter.MATCH_ALL_PATTERN;

@ServerFilter(MATCH_ALL_PATTERN)
public class CorrelationIdFilter {

    @RequestFilter
    public void onRequest(HttpRequest<?> request, MutablePropagatedContext propagatedContext) {
        String id = request.getHeaders().get(CorrelationId.HEADER);
        if (id == null || id.isBlank()) {
            id = UUID.randomUUID().toString();
        }
        request.setAttribute(CorrelationId.ATTRIBUTE, id);
        propagatedContext.add(new MdcPropagationElement(id));
    }

    @ResponseFilter
    public void onResponse(HttpRequest<?> request, MutableHttpResponse<?> response) {
        String id = CorrelationId.of(request);
        if (id != null) {
            response.header(CorrelationId.HEADER, id);
        }
    }
}
