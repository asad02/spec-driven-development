package com.example.featuretoggle.api;

import com.example.featuretoggle.exception.AccessDeniedForFeatureException;
import com.example.featuretoggle.ff4j.FeatureCatalog;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Locale;
import java.util.Map;

/**
 * The runtime surface: routing for nginx, access decisions for the product
 * backends, and the toggle the product UI shows.
 *
 * <p>Administration lives under /api/v1/admin — see FeatureAdminController.
 */
@RestController
@RequestMapping("/api/v1/features")
@Tag(name = "Feature evaluation")
public class FeatureController {

    /** Header nginx reads via auth_request to pick an upstream. */
    public static final String BACKEND_HOST_HEADER = "X-Backend-Host";

    private final FeatureEvaluationService evaluation;

    public FeatureController(FeatureEvaluationService evaluation) {
        this.evaluation = evaluation;
    }

    @GetMapping
    @Operation(summary = "Runtime summary", description = "Active backend and the caller's rights.")
    public Map<String, Object> current() {
        return state();
    }

    /**
     * The nginx auth_request target. Anonymous and body-less: it is called once per
     * proxied API request, so it must stay cheap.
     */
    @GetMapping("/route")
    @Operation(summary = "Routing decision for nginx",
            description = "Internal. 204 with X-Backend-Host naming the upstream to use.")
    @ApiResponse(responseCode = "204", description = "Header names the upstream")
    public ResponseEntity<Void> route() {
        BackendProvider active = evaluation.activeBackend();
        return ResponseEntity.noContent()
                .header(BACKEND_HOST_HEADER, active.host())
                .header("Cache-Control", "no-store")
                .build();
    }

    /**
     * The decision endpoint the product backends call, forwarding the end user's
     * token so the roles evaluated here are the caller's own.
     */
    @GetMapping("/{uid}/access")
    @Operation(summary = "May this caller use this feature?",
            description = "Called by the product backends with the end user's bearer token.")
    public FeatureDecision access(@PathVariable String uid) {
        return evaluation.decide(uid);
    }

    @PutMapping
    @Operation(summary = "Switch the active backend",
            description = "Takes effect on the next request — no restart, no redeploy.")
    public Map<String, Object> switchBackend(@RequestBody Map<String, String> body) {
        // Gated by the adminRole custom property rather than FF4J_ROLES: the flag
        // itself must stay evaluable by nginx's anonymous subrequest.
        if (!evaluation.isAdminFor(FeatureCatalog.BACKEND_ROUTING)) {
            throw new AccessDeniedForFeatureException(FeatureCatalog.BACKEND_ROUTING,
                    evaluation.deniedMessage(FeatureCatalog.BACKEND_ROUTING,
                            "Switching the active backend requires an administrator role."));
        }
        String requested = body.getOrDefault("activeBackend", "").toLowerCase(Locale.ROOT);
        BackendProvider target = switch (requested) {
            case "springboot" -> BackendProvider.SPRINGBOOT;
            case "micronaut" -> BackendProvider.MICRONAUT;
            default -> throw new com.example.featuretoggle.exception.InvalidParameterException(
                    "activeBackend", "activeBackend must be micronaut or springboot.");
        };
        evaluation.setActiveBackend(target);
        return state();
    }

    /**
     * One payload for both GET and PUT. They must not diverge: the UI applies
     * whatever comes back, so a PUT omitting the rights fields would make the
     * caller appear to lose them the moment they used the toggle.
     */
    private Map<String, Object> state() {
        BackendProvider active = evaluation.activeBackend();
        FeatureDecision data = evaluation.decide(FeatureCatalog.USER_DATA_ACCESS);
        return Map.of(
                "activeBackend", active.id(),
                "upstreamHost", active.host(),
                "flag", FeatureCatalog.BACKEND_ROUTING,
                "store", "ff4j / feature-toggle database",
                "canToggle", evaluation.isAdminFor(FeatureCatalog.BACKEND_ROUTING),
                "canAccessData", data.allowed(),
                "readOnly", data.readOnly(),
                "dataRoles", data.allowedRoles());
    }
}
