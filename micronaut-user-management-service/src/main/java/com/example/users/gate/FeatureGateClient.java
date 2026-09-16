package com.example.users.gate;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.micronaut.context.annotation.Value;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Asks the feature service whether the caller may use a feature.
 *
 * <p>This service holds no feature rules and no flag library. It forwards the
 * caller's own bearer token so the roles evaluated are theirs.
 *
 * <p><strong>Fails open.</strong> If the feature service cannot be reached, access
 * is allowed and a warning is logged: authentication has already succeeded, and
 * refusing everything would turn a dependency outage into a total outage. Denials
 * come only from a policy that exists and excludes the caller.
 */
@Singleton
public class FeatureGateClient {

    private static final Logger LOG = LoggerFactory.getLogger(FeatureGateClient.class);

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(2)).build();
    private final ObjectMapper json = new ObjectMapper();
    private final Map<String, Cached> cache = new ConcurrentHashMap<>();

    private final String baseUrl;
    private final Duration ttl;

    public FeatureGateClient(@Value("${app.feature-service.url:http://feature-toggle-management-service:8080}") String baseUrl,
                             @Value("${app.feature-service.cache-ttl-seconds:10}") Integer ttlSeconds) {
        this.baseUrl = baseUrl;
        this.ttl = Duration.ofSeconds(ttlSeconds == null ? 10 : ttlSeconds);
    }

    public FeatureDecision decide(String featureUid, String bearerToken) {
        // Keyed by token as well as feature: the decision depends on the caller's
        // roles, so one entry per feature would leak one operator's rights to another.
        String key = featureUid + '|' + bearerToken;
        Cached cached = cache.get(key);
        if (cached != null && cached.expiresAt().isAfter(Instant.now())) {
            return cached.decision();
        }

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/v1/features/" + featureUid + "/access"))
                    .timeout(Duration.ofSeconds(3))
                    .header("Authorization", "Bearer " + bearerToken)
                    .GET().build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                LOG.warn("Feature service answered {} for '{}'; allowing", response.statusCode(), featureUid);
                return FeatureDecision.allowAll(featureUid);
            }
            FeatureDecision decision = json.readValue(response.body(), FeatureDecision.class);
            cache.put(key, new Cached(decision, Instant.now().plus(ttl)));
            return decision;
        } catch (Exception ex) {
            LOG.warn("Feature service unreachable for '{}'; allowing. {}", featureUid, ex.toString());
            return FeatureDecision.allowAll(featureUid);
        }
    }

    private record Cached(FeatureDecision decision, Instant expiresAt) {
    }
}
