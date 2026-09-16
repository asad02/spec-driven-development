package com.example.users.config;

import io.micronaut.core.propagation.ThreadPropagatedContextElement;
import org.slf4j.MDC;

/**
 * Carries the correlation id across Micronaut's thread switches, so the id set
 * on the event loop is still in the MDC when the controller runs on a blocking
 * thread — which is what makes NFR-6 true in the logs and not just the header.
 */
public record MdcPropagationElement(String correlationId) implements ThreadPropagatedContextElement<String> {

    @Override
    public String updateThreadContext() {
        String previous = MDC.get(CorrelationId.MDC_KEY);
        MDC.put(CorrelationId.MDC_KEY, correlationId);
        return previous;
    }

    @Override
    public void restoreThreadContext(String oldState) {
        if (oldState == null) {
            MDC.remove(CorrelationId.MDC_KEY);
        } else {
            MDC.put(CorrelationId.MDC_KEY, oldState);
        }
    }
}
