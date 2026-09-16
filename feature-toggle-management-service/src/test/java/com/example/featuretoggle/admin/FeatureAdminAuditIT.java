package com.example.featuretoggle.admin;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * FR-12 and BR-10. The trail exists so that "who turned off access at 3 a.m. and
 * what was it before?" has an answer — which means refused attempts matter as much
 * as successful ones, and a change that cannot be recorded must not happen.
 */
@DisplayName("admin API: audit trail")
class FeatureAdminAuditIT extends AbstractAdminIT {

    private List<JsonNode> auditEntries() {
        HttpResponse<String> response = send("GET", FEATURES + "/audit?limit=200", adminToken);
        assertThat(response.statusCode()).isEqualTo(200);
        List<JsonNode> entries = new ArrayList<>();
        json(response).forEach(entries::add);
        return entries;
    }

    private boolean hasEntry(String action, String feature) {
        return auditEntries().stream().anyMatch(e ->
                e.get("action").asString().equals(action) && e.get("feature").asString().equals(feature));
    }

    @Test
    @DisplayName("a successful change is recorded with who, what and the before/after")
    void changesAreRecorded() {
        String uid = createFeature("audit-change-" + System.nanoTime());
        send("PUT", FEATURES + "/" + uid, adminToken,
                "{\"uid\":\"" + uid + "\",\"description\":\"\",\"enabled\":true,\"roles\":[]}");

        JsonNode update = auditEntries().stream()
                .filter(e -> e.get("feature").asString().equals(uid) && e.get("action").asString().equals("UPDATE"))
                .findFirst().orElseThrow();

        assertThat(update.get("user").asString()).isEqualTo("admin");
        assertThat(update.get("detail").asString()).contains("false").contains("true");
        assertThat(update.get("at").asString()).isNotEmpty();

        assertThat(hasEntry("CREATE", uid)).isTrue();
        send("DELETE", FEATURES + "/" + uid, adminToken);
    }

    @Test
    @DisplayName("role grants and revocations are recorded individually")
    void roleChangesAreRecorded() {
        String uid = createFeature("audit-roles-" + System.nanoTime());
        send("POST", FEATURES + "/" + uid + "/roles/ROLE_VIEWER", adminToken);
        send("DELETE", FEATURES + "/" + uid + "/roles/ROLE_VIEWER", adminToken);

        assertThat(hasEntry("GRANT_ROLE", uid)).isTrue();
        assertThat(hasEntry("REVOKE_ROLE", uid)).isTrue();
        send("DELETE", FEATURES + "/" + uid, adminToken);
    }

    @Test
    @DisplayName("a refusal on role grounds is recorded — probing what you cannot do is the point")
    void roleRefusalsAreRecorded() {
        send("POST", FEATURES, viewerToken,
                "{\"uid\":\"audit-refused-create\",\"description\":\"\",\"enabled\":false,\"roles\":[]}");

        JsonNode refusal = auditEntries().stream()
                .filter(e -> e.get("action").asString().equals("CREATE_REFUSED"))
                .findFirst().orElseThrow();

        assertThat(refusal.get("user").asString()).isEqualTo("viewer");
        assertThat(refusal.get("detail").asString()).contains("ROLE_ADMIN");
    }

    @Test
    @DisplayName("a refusal on guardrail grounds is recorded, with the full explanation")
    void guardrailRefusalsAreRecorded() {
        send("DELETE", FEATURES + "/" + PROTECTED_FEATURE, adminToken);

        JsonNode refusal = auditEntries().stream()
                .filter(e -> e.get("action").asString().equals("DELETE_REFUSED")
                        && e.get("feature").asString().equals(PROTECTED_FEATURE))
                .findFirst().orElseThrow();

        // The whole reason must survive: EVT_VALUE was VARCHAR(100) in ff4j's shipped
        // schema and silently dropped these rows until V2 widened it.
        assertThat(refusal.get("detail").asString())
                .contains("protected")
                .hasSizeGreaterThan(60);
    }

    @Test
    @DisplayName("the trail is append-only — there is no endpoint that removes an entry")
    void auditCannotBeCleared() {
        int before = auditEntries().size();
        assertThat(send("DELETE", FEATURES + "/audit", adminToken).statusCode()).isIn(404, 405);
        assertThat(auditEntries().size()).isGreaterThanOrEqualTo(before);
    }

    @Test
    @DisplayName("a read-only operator can read the trail but cannot write to it")
    void viewerCanReadAudit() {
        assertThat(send("GET", FEATURES + "/audit", viewerToken).statusCode()).isEqualTo(200);
        assertThat(send("POST", FEATURES + "/audit", viewerToken, "{}").statusCode()).isIn(403, 404, 405);
    }

    @Test
    @DisplayName("entries are newest first, so the last change is the first thing read")
    void trailIsReverseChronological() {
        String uid = createFeature("audit-order-" + System.nanoTime());
        List<JsonNode> entries = auditEntries();

        assertThat(entries).isNotEmpty();
        for (int i = 1; i < entries.size(); i++) {
            assertThat(entries.get(i - 1).get("at").asString())
                    .isGreaterThanOrEqualTo(entries.get(i).get("at").asString());
        }
        send("DELETE", FEATURES + "/" + uid, adminToken);
    }
}
