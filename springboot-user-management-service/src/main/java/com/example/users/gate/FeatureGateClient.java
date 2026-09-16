package com.example.users.gate;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

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
 * <p>This service holds no feature rules of its own and no flag library — it
 * forwards the caller's own bearer token so the roles evaluated are theirs, and
 * applies the answer.
 *
 * <p><strong>Fails open.</strong> If the feature service cannot be reached, access
 * is allowed and a warning is logged. Authentication has already succeeded by this
 * point; refusing every request would turn a dependency outage into a total
 * outage, which is a worse failure than briefly not enforcing a role split among
 * already-authenticated operators. Denials come only from a policy that exists and
 * excludes the caller.
 */
@Component
public class FeatureGateClient {

    private static final Logger LOG = LoggerFactory.getLogger(FeatureGateClient.class);

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(2)).build();
    private final ObjectMapper json = new ObjectMapper();
    private final Map<String, CachedDecision> cache = new ConcurrentHashMap<>();

    private final String baseUrl;
    private final Duration ttl;

    public FeatureGateClient(@Value("${app.feature-service.url:http://feature-toggle-management-service:8080}") String baseUrl,
                             @Value("${app.feature-service.cache-ttl-seconds:10}") long ttlSeconds) {
        this.baseUrl = baseUrl;
        this.ttl = Duration.ofSeconds(ttlSeconds);
    }

    public FeatureDecision decide(String featureUid, String bearerToken) {
        // Keyed by token as well as feature: the decision depends on the caller's
        // roles, so one cache entry per feature would leak one operator's rights
        // to another.
        String key = featureUid + '|' + bearerToken;
        CachedDecision cached = cache.get(key);
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
            cache.put(key, new CachedDecision(decision, Instant.now().plus(ttl)));
            return decision;
        } catch (Exception ex) {
            LOG.warn("Feature service unreachable for '{}'; allowing. {}", featureUid, ex.toString());
            return FeatureDecision.allowAll(featureUid);
        }
    }

    private record CachedDecision(FeatureDecision decision, Instant expiresAt) {
    }
}
