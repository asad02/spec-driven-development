package com.example.featuretoggle.admin;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.net.http.HttpResponse;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Each row of technical spec §6.4, asserted server-side with the UI bypassed
 * entirely. These matter more than any screen test: a UI bug is visible, whereas a
 * missing guardrail is invisible until somebody is locked out of the system with no
 * route back through the application.
 */
@DisplayName("admin API: lockout guardrails")
class FeatureAdminGuardrailIT extends AbstractAdminIT {

    @AfterEach
    void restoreProtectedAcl() {
        // Leave the ACL as found; these tests deliberately take it to the edge.
        send("POST", FEATURES + "/" + PROTECTED_FEATURE + "/roles/ROLE_ADMIN", adminToken);
        send("POST", FEATURES + "/" + PROTECTED_FEATURE + "/roles/ROLE_VIEWER", adminToken);
    }

    @Test
    @DisplayName("BR-9: a protected feature cannot be deleted, even by an administrator")
    void protectedFeatureCannotBeDeleted() {
        HttpResponse<String> response = send("DELETE", FEATURES + "/" + PROTECTED_FEATURE, adminToken);

        assertThat(response.statusCode()).isEqualTo(409);
        assertThat(json(response).get("code").asString()).isEqualTo("FEATURE_PROTECTED");
        // The refusal must say what would have broken, not merely that it was refused.
        assertThat(json(response).get("message").asString()).contains("protected");

        assertThat(send("GET", FEATURES + "/" + PROTECTED_FEATURE, adminToken).statusCode()).isEqualTo(200);
    }

    @Test
    @DisplayName("BR-8: the last role cannot be removed from a protected feature")
    void lastRoleCannotBeRemoved() {
        // Down to one role — allowed, because one remains.
        assertThat(send("DELETE", FEATURES + "/" + PROTECTED_FEATURE + "/roles/ROLE_VIEWER", adminToken)
                .statusCode()).isEqualTo(200);

        HttpResponse<String> response =
                send("DELETE", FEATURES + "/" + PROTECTED_FEATURE + "/roles/ROLE_ADMIN", adminToken);

        assertThat(response.statusCode()).isEqualTo(409);
        assertThat(json(response).get("code").asString()).isEqualTo("FEATURE_PROTECTED");
        assertThat(json(response).get("message").asString()).contains("last role");

        // The system is still administrable — the point of the guardrail.
        assertThat(json(send("GET", FEATURES + "/" + PROTECTED_FEATURE, adminToken))
                .get("roles").toString()).contains("ROLE_ADMIN");
    }

    @Test
    @DisplayName("an unprotected feature has no such restriction")
    void unprotectedFeatureIsFreelyEditable() {
        String uid = createFeature("guardrail-free-" + System.nanoTime());

        assertThat(send("POST", FEATURES + "/" + uid + "/roles/ROLE_ADMIN", adminToken).statusCode()).isEqualTo(200);
        // Removing its only role is fine: nothing depends on this feature.
        assertThat(send("DELETE", FEATURES + "/" + uid + "/roles/ROLE_ADMIN", adminToken).statusCode()).isEqualTo(200);
        assertThat(send("DELETE", FEATURES + "/" + uid, adminToken).statusCode()).isEqualTo(204);
        assertThat(send("GET", FEATURES + "/" + uid, adminToken).statusCode()).isEqualTo(404);
    }

    @Test
    @DisplayName("a protected feature may still be disabled while a role can re-enable it")
    void protectedFeatureMayBeDisabled() {
        HttpResponse<String> off = send("PUT", FEATURES + "/" + PROTECTED_FEATURE, adminToken,
                "{\"uid\":\"" + PROTECTED_FEATURE + "\",\"description\":\"\",\"enabled\":false,\"roles\":[]}");
        assertThat(off.statusCode()).isEqualTo(200);
        assertThat(json(off).get("enabled").asBoolean()).isFalse();

        HttpResponse<String> on = send("PUT", FEATURES + "/" + PROTECTED_FEATURE, adminToken,
                "{\"uid\":\"" + PROTECTED_FEATURE + "\",\"description\":\"\",\"enabled\":true,\"roles\":[]}");
        assertThat(json(on).get("enabled").asBoolean()).isTrue();
    }

    @Test
    @DisplayName("protected status is server-computed and cannot be set by a client")
    void protectedFlagIsNotClientSupplied() {
        String uid = createFeature("guardrail-claim-" + System.nanoTime());

        // Claim protection we were not granted (BR-5: unknown fields are ignored).
        send("PUT", FEATURES + "/" + uid, adminToken,
                "{\"uid\":\"" + uid + "\",\"description\":\"\",\"enabled\":false,\"roles\":[],\"isProtected\":true}");

        assertThat(json(send("GET", FEATURES + "/" + uid, adminToken)).get("isProtected").asBoolean()).isFalse();
        assertThat(send("DELETE", FEATURES + "/" + uid, adminToken).statusCode()).isEqualTo(204);
    }

    @Test
    @DisplayName("creating a feature that already exists is a conflict, not an overwrite")
    void duplicateCreateIsRefused() {
        String uid = createFeature("guardrail-dup-" + System.nanoTime());
        HttpResponse<String> response = send("POST", FEATURES, adminToken,
                "{\"uid\":\"" + uid + "\",\"description\":\"different\",\"enabled\":true,\"roles\":[]}");

        assertThat(response.statusCode()).isEqualTo(409);
        assertThat(json(response).get("code").asString()).isEqualTo("FEATURE_ALREADY_EXISTS");
        // The original is untouched.
        assertThat(json(send("GET", FEATURES + "/" + uid, adminToken)).get("enabled").asBoolean()).isFalse();

        send("DELETE", FEATURES + "/" + uid, adminToken);
    }

    @Test
    @DisplayName("an unknown feature is a 404, not a silent create")
    void unknownFeatureIsNotFound() {
        assertThat(send("GET", FEATURES + "/no-such-feature", adminToken).statusCode()).isEqualTo(404);
        assertThat(send("DELETE", FEATURES + "/no-such-feature", adminToken).statusCode()).isEqualTo(404);
    }
}
