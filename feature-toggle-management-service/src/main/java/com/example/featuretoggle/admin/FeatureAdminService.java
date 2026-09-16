package com.example.featuretoggle.admin;

import com.example.featuretoggle.exception.FeatureAlreadyExistsException;
import com.example.featuretoggle.exception.FeatureNotFoundException;
import com.example.featuretoggle.exception.FeatureProtectedException;
import com.example.featuretoggle.ff4j.CallerRoles;
import com.example.featuretoggle.ff4j.FeatureCatalog;
import org.ff4j.FF4j;
import org.ff4j.audit.Event;
import org.ff4j.core.Feature;
import org.ff4j.property.Property;
import org.ff4j.property.PropertyString;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * All mutation of the feature store goes through here, so the guardrails and the
 * audit trail cannot be bypassed by reaching ff4j directly from a controller.
 */
@Service
public class FeatureAdminService {

    private static final Logger LOG = LoggerFactory.getLogger(FeatureAdminService.class);
    // Deliberately NOT renamed alongside the service. This is the value written to
    // ff4j_audit.evt_source and the filter auditTrail() reads back with, so changing
    // it hides every audit entry recorded before the change. Stored data, like the
    // use-springboot-backend feature uid — not a display string.
    private static final String SOURCE = "feature-toggle-management";

    private final FF4j ff4j;
    private final CallerRoles authorizations;
    private final FeatureAdminProperties properties;
    private final JdbcTemplate jdbc;

    public FeatureAdminService(FF4j ff4j, CallerRoles authorizations,
                               FeatureAdminProperties properties, JdbcTemplate jdbc) {
        this.ff4j = ff4j;
        this.authorizations = authorizations;
        this.properties = properties;
        this.jdbc = jdbc;
    }

    // ---------- reads ----------

    public List<FeatureView> list() {
        return ff4j.getFeatureStore().readAll().values().stream()
                .map(this::toView)
                .sorted(Comparator.comparing(FeatureView::uid))
                .toList();
    }

    public FeatureView get(String uid) {
        return toView(require(uid));
    }

    // ---------- writes ----------

    public FeatureView create(FeatureUpsertRequest request) {
        if (ff4j.getFeatureStore().exist(request.uid())) {
            throw new FeatureAlreadyExistsException(request.uid());
        }
        Feature feature = new Feature(request.uid(), request.enabled(),
                request.description() == null ? "" : request.description());
        if (request.roles() != null && !request.roles().isEmpty()) {
            feature.setPermissions(new LinkedHashSet<>(request.roles()));
        }
        audit("CREATE", request.uid(), "enabled=" + request.enabled()
                + " roles=" + (request.roles() == null ? "[]" : request.roles()));
        ff4j.getFeatureStore().create(feature);
        return toView(require(request.uid()));
    }

    public FeatureView update(String uid, FeatureUpsertRequest request) {
        Feature feature = require(uid);
        boolean wasEnabled = feature.isEnable();

        if (!request.enabled() && wasEnabled) {
            try {
                guardDisable(uid);
            } catch (FeatureProtectedException ex) {
                auditRefusal("UPDATE", uid, ex.getMessage());
                throw ex;
            }
        }
        feature.setEnable(request.enabled());
        if (request.description() != null) {
            feature.setDescription(request.description());
        }
        audit("UPDATE", uid, "enabled " + wasEnabled + " -> " + request.enabled());
        ff4j.getFeatureStore().update(feature);
        return toView(require(uid));
    }

    public void delete(String uid) {
        require(uid);
        if (isProtected(uid)) {
            // Record the refusal before raising it: a guardrail refusal is exactly the
            // kind of attempt FR-12 exists to capture, and the throw would otherwise
            // skip audit() entirely.
            String reason = "'" + uid + "' is protected: " + protectedReason(uid)
                    + " Deleting it would leave that behaviour unconfigurable.";
            auditRefusal("DELETE", uid, reason);
            throw new FeatureProtectedException(uid, reason);
        }
        audit("DELETE", uid, "feature removed");
        ff4j.getFeatureStore().delete(uid);
    }

    public FeatureView grantRole(String uid, String role) {
        Feature feature = require(uid);
        Set<String> roles = currentRoles(feature);
        if (roles.add(role)) {
            feature.setPermissions(roles);
            audit("GRANT_ROLE", uid, role);
            ff4j.getFeatureStore().update(feature);
        }
        return toView(require(uid));
    }

    public FeatureView revokeRole(String uid, String role) {
        Feature feature = require(uid);
        Set<String> roles = currentRoles(feature);
        if (!roles.contains(role)) {
            return toView(feature);
        }
        try {
            guardRevoke(uid, roles, role);
        } catch (FeatureProtectedException ex) {
            auditRefusal("REVOKE_ROLE", uid, ex.getMessage());
            throw ex;
        }
        roles.remove(role);
        feature.setPermissions(roles);
        audit("REVOKE_ROLE", uid, role);
        ff4j.getFeatureStore().update(feature);
        return toView(require(uid));
    }

    public FeatureView setProperty(String uid, String key, String value) {
        Feature feature = require(uid);
        Map<String, Property<?>> props = feature.getCustomProperties() == null
                ? new LinkedHashMap<>() : new LinkedHashMap<>(feature.getCustomProperties());
        String before = props.containsKey(key) ? String.valueOf(props.get(key).getValue()) : "(unset)";
        props.put(key, new PropertyString(key, value));
        feature.setCustomProperties(props);
        audit("SET_PROPERTY", uid, key + ": " + before + " -> " + value);
        ff4j.getFeatureStore().update(feature);
        return toView(require(uid));
    }

    public FeatureView removeProperty(String uid, String key) {
        Feature feature = require(uid);
        Map<String, Property<?>> props = feature.getCustomProperties() == null
                ? new LinkedHashMap<>() : new LinkedHashMap<>(feature.getCustomProperties());
        if (props.remove(key) != null) {
            feature.setCustomProperties(props);
            audit("REMOVE_PROPERTY", uid, key);
            ff4j.getFeatureStore().update(feature);
        }
        return toView(require(uid));
    }

    // ---------- guardrails (functional spec BR-8, BR-9) ----------

    private void guardDisable(String uid) {
        // Disabling a protected feature is allowed only while a role remains that
        // could re-enable it — otherwise the console disables its own way back.
        if (isProtected(uid) && currentRoles(require(uid)).isEmpty()
                && FeatureCatalog.USER_DATA_ACCESS.equals(uid)) {
            throw new FeatureProtectedException(uid,
                    "'" + uid + "' has no roles, so disabling it would leave nobody able to re-enable it.");
        }
    }

    private void guardRevoke(String uid, Set<String> roles, String role) {
        if (!isProtected(uid)) {
            return;
        }
        if (roles.size() <= 1) {
            throw new FeatureProtectedException(uid,
                    "'" + role + "' is the last role on protected feature '" + uid
                            + "'. Removing it would lock every operator out of " + protectedReason(uid));
        }
        // Never let a caller remove the role that is granting them this very access.
        Set<String> mine = authorizations.getCurrentUserPermissions();
        if (mine.contains(role) && mine.stream().filter(roles::contains).count() <= 1) {
            throw new FeatureProtectedException(uid,
                    "'" + role + "' is the only role granting you access to '" + uid
                            + "'. Removing it would lock you out immediately.");
        }
    }

    // ---------- audit (functional spec FR-12, BR-10) ----------

    /**
     * Recorded BEFORE the change is applied. If this throws, the caller's change
     * never happens — an unrecorded change to access control is worse than a
     * failed one.
     */
    private void audit(String action, String feature, String detail) {
        Event event = new Event(SOURCE, "feature", feature, action);
        event.setUser(authorizations.getCurrentUserName());
        event.setValue(fit(detail));
        boolean saved = ff4j.getEventRepository().saveEvent(event);
        if (!saved) {
            throw new IllegalStateException(
                    "Could not record the audit entry for " + action + " on '" + feature
                            + "'; the change was not applied.");
        }
        LOG.info("audit {} {} by {} — {}", action, feature, event.getUser(), detail);
    }

    /** Records an attempt that the guardrails or authorisation refused. */
    public void auditRefusal(String action, String feature, String reason) {
        try {
            Event event = new Event(SOURCE, "feature", feature, action + "_REFUSED");
            event.setUser(authorizations.getCurrentUserName());
            event.setValue(fit(reason));
            ff4j.getEventRepository().saveEvent(event);
        } catch (RuntimeException ex) {
            // A refusal is already being returned; failing to log it must not mask that.
            LOG.warn("Could not record refused {} on '{}'", action, feature, ex);
        }
    }

    // ---------- helpers ----------

    /**
     * Keeps the detail inside EVT_VALUE. V2 widened the column to 1000, but a
     * belt-and-braces trim means a future long message degrades to a shortened
     * audit entry rather than a failed insert — which, under BR-10, would block
     * the change itself.
     */
    private static String fit(String detail) {
        if (detail == null) {
            return null;
        }
        return detail.length() <= 1000 ? detail : detail.substring(0, 997) + "...";
    }

    private Feature require(String uid) {
        if (!ff4j.getFeatureStore().exist(uid)) {
            throw new FeatureNotFoundException(uid);
        }
        return ff4j.getFeatureStore().read(uid);
    }

    private static Set<String> currentRoles(Feature feature) {
        return feature.getPermissions() == null
                ? new LinkedHashSet<>() : new LinkedHashSet<>(feature.getPermissions());
    }

    public boolean isProtected(String uid) {
        return properties.getProtectedFeatures().contains(uid);
    }

    private String protectedReason(String uid) {
        if (FeatureCatalog.USER_DATA_ACCESS.equals(uid)) {
            return "user data.";
        }
        if (FeatureCatalog.BACKEND_ROUTING.equals(uid)) {
            return "backend routing.";
        }
        return "a capability the system depends on.";
    }

    private FeatureView toView(Feature feature) {
        Map<String, String> props = new LinkedHashMap<>();
        if (feature.getCustomProperties() != null) {
            feature.getCustomProperties().forEach((k, v) ->
                    props.put(k, v.getValue() == null ? null : String.valueOf(v.getValue())));
        }
        List<String> roles = new ArrayList<>(currentRoles(feature));
        roles.sort(Comparator.naturalOrder());
        boolean prot = isProtected(feature.getUid());
        return new FeatureView(feature.getUid(), feature.isEnable(), feature.getDescription(),
                roles, props, prot, prot ? protectedReason(feature.getUid()) : null);
    }

    /**
     * Reads FF4J_AUDIT directly rather than through ff4j's EventQueryDefinition,
     * which is built around time-bucketed usage analytics rather than a plain
     * reverse-chronological trail. The table is what JdbcEventRepository writes,
     * so this stays consistent with what audit() records.
     */
    public List<AuditEntry> auditTrail(int limit) {
        return jdbc.query("""
                SELECT evt_time, evt_user, evt_action, evt_name, evt_value
                FROM ff4j_audit
                WHERE evt_source = ?
                ORDER BY evt_time DESC
                LIMIT ?
                """,
                (rs, i) -> new AuditEntry(
                        rs.getTimestamp("evt_time").toInstant(),
                        rs.getString("evt_user"),
                        rs.getString("evt_action"),
                        rs.getString("evt_name"),
                        rs.getString("evt_value")),
                SOURCE, limit);
    }
}
