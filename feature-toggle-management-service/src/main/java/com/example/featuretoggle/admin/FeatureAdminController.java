package com.example.featuretoggle.admin;

import com.example.featuretoggle.exception.AccessDeniedForFeatureException;
import com.example.featuretoggle.ff4j.CallerRoles;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Set;
import java.util.Map;

/**
 * The administrative surface consumed by the feature-toggle-management-ui SPA.
 *
 * <p>Not registered at all unless {@code app.feature-admin.enabled} is true, so a
 * disabled console returns 404 rather than 403 — it should not advertise that it
 * exists (technical spec §5).
 *
 * <p>Roles are checked here, server-side. The SPA hides controls a read-only
 * operator cannot use, but hiding is presentation; this is the protection.
 */
@RestController
@RequestMapping("/api/v1/admin/features")
@Tag(name = "Feature administration")
@ConditionalOnProperty(prefix = "app.feature-admin", name = "enabled", havingValue = "true")
public class FeatureAdminController {

    private static final String ROLE_ADMIN = "ROLE_ADMIN";
    private static final String ROLE_VIEWER = "ROLE_VIEWER";

    private final FeatureAdminService service;
    private final CallerRoles authorizations;

    public FeatureAdminController(FeatureAdminService service, CallerRoles authorizations) {
        this.service = service;
        this.authorizations = authorizations;
    }

    // ---------- reads: admin or viewer ----------

    @GetMapping
    @Operation(summary = "List features", description = "Every feature with its state, roles and properties.")
    public List<FeatureView> list() {
        requireRead();
        return service.list();
    }

    @GetMapping("/audit")
    @Operation(summary = "Change history", description = "Newest first. Append-only; there is no delete.")
    public List<AuditEntry> audit(@RequestParam(defaultValue = "100") int limit) {
        requireRead();
        return service.auditTrail(Math.min(Math.max(limit, 1), 500));
    }

    @GetMapping("/{uid}")
    @Operation(summary = "Get one feature")
    public FeatureView get(@PathVariable String uid) {
        requireRead();
        return service.get(uid);
    }

    // ---------- writes: admin only ----------

    @PostMapping
    @Operation(summary = "Create a feature",
            description = "A created flag does nothing until code reads it.")
    public ResponseEntity<FeatureView> create(@Valid @RequestBody FeatureUpsertRequest request) {
        requireAdmin("CREATE", request.uid());
        FeatureView created = service.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PutMapping("/{uid}")
    @Operation(summary = "Update a feature's state or description")
    public FeatureView update(@PathVariable String uid, @Valid @RequestBody FeatureUpsertRequest request) {
        requireAdmin("UPDATE", uid);
        return service.update(uid, request);
    }

    @DeleteMapping("/{uid}")
    @Operation(summary = "Delete a feature", description = "Refused for protected features.")
    public ResponseEntity<Void> delete(@PathVariable String uid) {
        requireAdmin("DELETE", uid);
        service.delete(uid);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{uid}/roles/{role}")
    @Operation(summary = "Grant a role")
    public FeatureView grant(@PathVariable String uid, @PathVariable String role) {
        requireAdmin("GRANT_ROLE", uid);
        return service.grantRole(uid, role);
    }

    @DeleteMapping("/{uid}/roles/{role}")
    @Operation(summary = "Revoke a role", description = "Refused where it would lock everyone out.")
    public FeatureView revoke(@PathVariable String uid, @PathVariable String role) {
        requireAdmin("REVOKE_ROLE", uid);
        return service.revokeRole(uid, role);
    }

    @PutMapping("/{uid}/properties/{key}")
    @Operation(summary = "Set a custom property")
    public FeatureView setProperty(@PathVariable String uid, @PathVariable String key,
                                   @RequestBody Map<String, String> body) {
        requireAdmin("SET_PROPERTY", uid);
        return service.setProperty(uid, key, body.getOrDefault("value", ""));
    }

    @DeleteMapping("/{uid}/properties/{key}")
    @Operation(summary = "Remove a custom property")
    public FeatureView removeProperty(@PathVariable String uid, @PathVariable String key) {
        requireAdmin("REMOVE_PROPERTY", uid);
        return service.removeProperty(uid, key);
    }

    // ---------- role checks ----------

    private void requireRead() {
        Set<String> roles = authorizations.getCurrentUserPermissions();
        if (!roles.contains(ROLE_ADMIN) && !roles.contains(ROLE_VIEWER)) {
            throw new AccessDeniedForFeatureException("feature-administration",
                    "Your role does not grant access to feature administration.");
        }
    }

    /** Refusals are recorded too — probing what you cannot do is what audit is for. */
    private void requireAdmin(String action, String uid) {
        Set<String> roles = authorizations.getCurrentUserPermissions();
        if (!roles.contains(ROLE_ADMIN)) {
            service.auditRefusal(action, uid, "caller roles " + roles + " lack ROLE_ADMIN");
            throw new AccessDeniedForFeatureException("feature-administration",
                    "Changing feature toggles requires an administrator role.");
        }
    }
}
