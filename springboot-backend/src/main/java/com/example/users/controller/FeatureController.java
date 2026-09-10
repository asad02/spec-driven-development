package com.example.users.controller;

import com.example.users.exception.AccessDeniedForFeatureException;
import com.example.users.feature.BackendProvider;
import com.example.users.feature.FeatureAccess;
import com.example.users.feature.FeatureAccessService;
import com.example.users.feature.FeatureService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Locale;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/features")
@Tag(name = "Feature flags")
public class FeatureController {

    /** Header nginx reads via auth_request to pick an upstream. */
    public static final String BACKEND_HOST_HEADER = "X-Backend-Host";

    private final FeatureService featureService;
    private final FeatureAccessService access;

    public FeatureController(FeatureService featureService, FeatureAccessService access) {
        this.featureService = featureService;
        this.access = access;
    }

    @GetMapping
    @Operation(summary = "Read the active backend", description = "Which implementation /api traffic is currently routed to.")
    public Map<String, Object> current() {
        return state();
    }

    /**
     * The single payload both GET and PUT return. They must not diverge: the UI
     * applies whatever comes back, so a PUT that omitted the rights fields would
     * make the caller appear to lose them the moment they used the toggle.
     */
    private Map<String, Object> state() {
        BackendProvider active = featureService.activeBackend();
        return Map.of(
                "activeBackend", active.id(),
                "upstreamHost", active.host(),
                "flag", FeatureAccess.BACKEND_ROUTING,
                "store", "ff4j / PostgreSQL",
                "canToggle", access.isAdminFor(FeatureAccess.BACKEND_ROUTING),
                "canAccessData", access.isAllowed(FeatureAccess.USER_DATA_ACCESS),
                "readOnly", access.isReadOnly(FeatureAccess.USER_DATA_ACCESS),
                "dataRoles", access.allowedRoles(FeatureAccess.USER_DATA_ACCESS));
    }

    /**
     * The nginx auth_request target. Returns no body — only the header that names
     * the upstream — and must stay cheap, because it is called once per proxied
     * API request.
     */
    @GetMapping("/route")
    @Operation(summary = "Routing decision for nginx",
            description = "Internal. Returns 204 with X-Backend-Host naming the upstream to use.")
    @ApiResponse(responseCode = "204", description = "Header names the upstream")
    public ResponseEntity<Void> route() {
        BackendProvider active = featureService.activeBackend();
        return ResponseEntity.noContent()
                .header(BACKEND_HOST_HEADER, active.host())
                .header("Cache-Control", "no-store")
                .build();
    }

    @PutMapping
    @Operation(summary = "Switch the active backend",
            description = "Flips the ff4j flag. Takes effect on the next API request — no restart, no redeploy.")
    public Map<String, Object> switchBackend(@RequestBody Map<String, String> body) {
        // Gated by the adminRole custom property rather than FF4J_ROLES: the flag
        // itself must stay evaluable by nginx's anonymous subrequest.
        if (!access.isAdminFor(FeatureAccess.BACKEND_ROUTING)) {
            throw new AccessDeniedForFeatureException(FeatureAccess.BACKEND_ROUTING,
                    access.deniedMessage(FeatureAccess.BACKEND_ROUTING,
                            "Switching the active backend requires an administrator role."));
        }
        String requested = body.getOrDefault("activeBackend", "").toLowerCase(Locale.ROOT);
        BackendProvider target = switch (requested) {
            case "springboot" -> BackendProvider.SPRINGBOOT;
            case "micronaut" -> BackendProvider.MICRONAUT;
            default -> throw new com.example.users.exception.InvalidParameterException(
                    "activeBackend", "activeBackend must be micronaut or springboot.");
        };
        featureService.setActiveBackend(target);
        return state();
    }
}
